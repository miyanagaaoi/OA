#!/usr/bin/env node
/**
 * tools/check-permission-seed.js
 * ---------------------------------------------------------------------------
 * 校验 `oa-deploy/sql/04-permissions.sql`（权限树种子 + 9 角色默认授权）。
 *
 * 用法：
 *   node tools/check-permission-seed.js oa-deploy/sql/04-permissions.sql
 *   node tools/check-permission-seed.js            # 省略参数时用上面的默认路径
 *
 * 输出：stdout 打印 JSON 报告（stats / errors / warnings）；
 *       存在 error 时退出码为 1，否则为 0（warning 不影响退出码）。
 *
 * 零第三方依赖（只用 Node 内置 fs / path）。**只做静态解析**，不连数据库。
 *
 * 校验清单：
 *   0. `sys_role` 的 9 个内置角色**全部被播种**、`role_scope` / `data_scope` 取值合法，
 *      且角色段出现在任何 `sys_role_permission`（以及 `sys_permission`）插入之前；
 *   1. 每个 INSERT 的 code 唯一；
 *   2. perm_type 只允许 menu / button / api；
 *   3. 菜单的 url 若非空须以 / 开头；
 *   4. 父 code 在**本次插入的集合**内存在（顶层为 NULL），且父链可达根（无环、无悬空）；
 *   5. 父先于子（按文件解析顺序断言：子行出现时其父已出现）；
 *   6. 同一父下的 sort_no 不重复；
 *   7. 角色授权只引用已定义的权限 code、且角色码属于 9 码白名单；
 *   8. 断言 admin 角色被授予**全部**权限 code；
 *   9. 断言 company_admin **不含** admin:user:export、admin:role:grant、admin:authz:*、admin:system:*、
 *      以及 `admin:role` 整支（含只读的 admin:role:list —— 「不可再授权」）；
 *   9b. 断言 employee **含** `flow:task:withdraw` 与 `portal:h5`，且**不含**任何审批动作；
 *   9c. 断言 group_leader / chairman **含** `admin:report` 节点与 `admin:report:*` 查看项，
 *       且**不含** `admin:report:export`；
 *   9d. 断言仅 admin 权限项（改派/终止/主数据导出/权限树勾选/报表导出）未被下放；
 *   9e. 断言每个角色的授权集合满足**祖先闭包**（`子 ∈ G ⇒ 父 ∈ G`）——
 *       服务端 `PermissionTreePolicy.requireAncestorClosed` 对违规集合一律 400；
 *  10. 统计并输出：角色数、菜单数、按钮数、授权行数、深度分布。
 *
 * 另附（warning，不影响退出码）：字段长度越界、权限项规模偏离 60–90（已裁定接受 94）、
 *   未被任何角色授权的权限码、`flow:*` 不是 button、声明了 INSERT 却解析不出数据行。
 *
 * 权威值域来源：`oa-deploy/sql/01-schema.sql` §3.1（sys_role.code 9 码）、§3.4（sys_permission）。
 */
'use strict';

const fs = require('fs');
const path = require('path');

const DEFAULT_FILE = 'oa-deploy/sql/04-permissions.sql';

// --- 权威值域（与 01-schema.sql 列注释一致）----------------------------------

/** `sys_role.code` 的 9 个内置角色码（唯一权威源：01-schema.sql §3.1 列注释） */
const ROLE_WHITELIST = [
  'admin',
  'company_admin',
  'employee',
  'dept_leader',
  'branch_leader',
  'subsidiary_gm',
  'finance_owner',
  'group_leader',
  'chairman',
];

/** `sys_permission.perm_type` 白名单（01-schema.sql §3.4 列注释） */
const PERM_TYPES = ['menu', 'button', 'api'];

/** `sys_role.role_scope` 值域（01-schema.sql §3.1） */
const ROLE_SCOPES = ['group', 'company'];

/** `sys_role.data_scope` 值域（01-schema.sql §3.1 的 CHECK 约束） */
const DATA_SCOPES = ['self', 'dept', 'company', 'group_all', 'group_category'];

/** company_admin 越权防护：不得持有的精确权限码 */
const COMPANY_ADMIN_FORBIDDEN_EXACT = ['admin:user:export', 'admin:role:grant'];

/** company_admin 越权防护：不得持有的前缀（`admin:authz:*`、`admin:system:*`、`admin:role` 整支） */
const COMPANY_ADMIN_FORBIDDEN_PREFIX = ['admin:authz:', 'admin:system:'];

/** company_admin 不得持有 `admin:role` 整支（含只读的 admin:role:list）—— 裁定「不可再授权」 */
const COMPANY_ADMIN_FORBIDDEN_ROLE_BRANCH = 'admin:role';

/** employee 必须持有的权限码（裁定 3：可撤回自己发起的单据 + H5 全员入口） */
const EMPLOYEE_REQUIRED = ['flow:task:withdraw', 'portal:h5'];

/** 集团层报表查看：必须具备 `admin:report` 菜单节点 + 至少一个非导出的报表查看项 */
const GROUP_REPORT_ROLES = ['group_leader', 'chairman'];
const GROUP_REPORT_NODE = 'admin:report';
const GROUP_REPORT_EXPORT = 'admin:report:export';

/** 仅 `admin` 可持有的权限项（改派 / 终止 / 主数据导出 / 权限树勾选 / 报表导出） */
const ADMIN_ONLY_CODES = [
  'flow:task:reassign',
  'flow:task:terminate',
  'admin:user:export',
  'admin:role:grant',
  'admin:report:export',
];

/** 任务书建议规模区间 */
const SIZE_MIN = 60;
const SIZE_MAX = 90;

const CODE_MAX = 64;
const NAME_MAX = 50;
const URL_MAX = 255;

// --- 文本工具 ---------------------------------------------------------------

function stripBom(text) {
  return text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
}

/** 去 SQL 单引号字面量的引号（支持 '' 转义），非字面量返回 null */
function unquote(token) {
  const t = token.trim();
  if (t.length < 2 || t[0] !== "'" || t[t.length - 1] !== "'") return null;
  return t.slice(1, -1).replace(/''/g, "'");
}

/** 按「顶层逗号」切分 SQL 值列表：忽略字符串内的逗号与括号内的逗号 */
function splitTopLevel(text) {
  const out = [];
  let buf = '';
  let depth = 0;
  let i = 0;
  while (i < text.length) {
    const ch = text[i];
    if (ch === "'") {
      buf += ch;
      i += 1;
      while (i < text.length) {
        if (text[i] === "'") {
          if (text[i + 1] === "'") {
            buf += "''";
            i += 2;
            continue;
          }
          buf += "'";
          i += 1;
          break;
        }
        buf += text[i];
        i += 1;
      }
      continue;
    }
    if (ch === '(') {
      depth += 1;
      buf += ch;
      i += 1;
      continue;
    }
    if (ch === ')') {
      depth -= 1;
      buf += ch;
      i += 1;
      continue;
    }
    if (ch === ',' && depth === 0) {
      out.push(buf.trim());
      buf = '';
      i += 1;
      continue;
    }
    buf += ch;
    i += 1;
  }
  if (buf.trim() !== '') out.push(buf.trim());
  return out;
}

// --- 解析 -------------------------------------------------------------------

const RE_PERMISSION_INSERT =
  /INSERT\s+INTO\s+sys_permission\s*\([^)]*\)\s*VALUES\s*\(([\s\S]*?)\)\s*ON\s+DUPLICATE\s+KEY\s+UPDATE/gi;

const RE_ROLE_GRANT_INSERT =
  /INSERT\s+INTO\s+sys_role_permission[\s\S]*?ON\s+p\.code\s+IN\s*\(([\s\S]*?)\)\s*WHERE\s+r\.code\s*=\s*'([a-z0-9_]+)'/gi;

/** `sys_role`（注意不能匹配到 sys_role_permission / sys_role_category：要求 `sys_role` 后紧跟 `(`） */
const RE_ROLE_INSERT =
  /INSERT\s+INTO\s+sys_role\s*\(([^)]*)\)\s*VALUES\s*([\s\S]*?)ON\s+DUPLICATE\s+KEY\s+UPDATE/gi;

const RE_PARENT_CODE = /WHERE\s+code\s*=\s*'((?:[^']|'')*)'/i;

/** 把多行 VALUES 体切成若干「元组内部文本」（不含外层括号），正确处理字符串与嵌套括号 */
function splitTuples(body) {
  const out = [];
  let i = 0;
  while (i < body.length) {
    while (i < body.length && body[i] !== '(') i += 1;
    if (i >= body.length) break;
    const start = i;
    let depth = 0;
    let inStr = false;
    let j = i;
    for (; j < body.length; j += 1) {
      const ch = body[j];
      if (inStr) {
        if (ch === "'") {
          if (body[j + 1] === "'") {
            j += 1;
            continue;
          }
          inStr = false;
        }
        continue;
      }
      if (ch === "'") {
        inStr = true;
        continue;
      }
      if (ch === '(') depth += 1;
      else if (ch === ')') {
        depth -= 1;
        if (depth === 0) {
          j += 1;
          break;
        }
      }
    }
    out.push(body.slice(start + 1, j - 1));
    i = j;
  }
  return out;
}

function parse(text, errors) {
  const permissions = [];
  const grants = [];
  const roles = [];
  const marks = { firstPermission: -1, firstRole: -1, firstGrant: -1, lastRole: -1 };

  let m;
  RE_PERMISSION_INSERT.lastIndex = 0;
  while ((m = RE_PERMISSION_INSERT.exec(text)) !== null) {
    if (marks.firstPermission < 0) marks.firstPermission = m.index;
    const fields = splitTopLevel(m[1]);
    if (fields.length !== 6) {
      errors.push(
        `sys_permission 的 VALUES 应为 6 个字段（parent_id, perm_type, code, name, url, sort_no），实际 ${fields.length}` +
          `（片段：${m[1].slice(0, 80)}…）`,
      );
      continue;
    }
    const parentRaw = fields[0];
    let parentCode = null;
    if (!/^NULL$/i.test(parentRaw)) {
      const hit = RE_PARENT_CODE.exec(parentRaw);
      if (!hit) {
        errors.push(`无法从 parent_id 表达式解析父权限码：${parentRaw}`);
      } else {
        parentCode = hit[1].replace(/''/g, "'");
      }
    }
    const type = unquote(fields[1]);
    const code = unquote(fields[2]);
    const name = unquote(fields[3]);
    const url = /^NULL$/i.test(fields[4]) ? null : unquote(fields[4]);
    const sortNo = Number.parseInt(fields[5], 10);
    permissions.push({ parentCode, type, code, name, url, sortNo });
  }

  RE_ROLE_INSERT.lastIndex = 0;
  while ((m = RE_ROLE_INSERT.exec(text)) !== null) {
    if (marks.firstRole < 0) marks.firstRole = m.index;
    marks.lastRole = m.index;
    splitTuples(m[2]).forEach((tuple) => {
      const fields = splitTopLevel(tuple);
      if (fields.length !== 5) {
        errors.push(
          `sys_role 的 VALUES 应为 5 个字段（code, name, role_scope, data_scope, remark），实际 ${fields.length}` +
            `（片段：${tuple.slice(0, 80)}…）`,
        );
        return;
      }
      roles.push({
        code: unquote(fields[0]),
        name: unquote(fields[1]),
        roleScope: unquote(fields[2]),
        dataScope: unquote(fields[3]),
        remark: /^NULL$/i.test(fields[4]) ? null : unquote(fields[4]),
      });
    });
  }

  RE_ROLE_GRANT_INSERT.lastIndex = 0;
  while ((m = RE_ROLE_GRANT_INSERT.exec(text)) !== null) {
    if (marks.firstGrant < 0) marks.firstGrant = m.index;
    const codes = splitTopLevel(m[1])
      .map(unquote)
      .filter((c) => c != null);
    grants.push({ role: m[2], codes });
  }

  return { permissions, grants, roles, marks };
}

// --- 校验 -------------------------------------------------------------------

function check(text, errors, warnings) {
  const { permissions, grants, roles, marks } = parse(text, errors);

  if (permissions.length === 0) {
    errors.push('未解析到任何 sys_permission 的 INSERT 行');
  }
  if (grants.length === 0) {
    errors.push('未解析到任何 sys_role_permission 的 INSERT 语句');
  }

  // --- 0：角色播种（9 码齐备 + role_scope / data_scope 值域） ------------------
  const roleByCode = new Map();
  for (const r of roles) {
    if (r.code == null) {
      errors.push('存在无法解析 code 的 sys_role 行');
      continue;
    }
    if (roleByCode.has(r.code)) errors.push(`sys_role 角色码重复播种：${r.code}`);
    roleByCode.set(r.code, r);
    if (!ROLE_WHITELIST.includes(r.code)) {
      errors.push(`sys_role 播种了 9 码白名单外的角色：${r.code}`);
    }
    if (!ROLE_SCOPES.includes(r.roleScope)) {
      errors.push(`role_scope 非法（${r.roleScope}）：${r.code}（只允许 ${ROLE_SCOPES.join(' / ')}）`);
    }
    if (!DATA_SCOPES.includes(r.dataScope)) {
      errors.push(`data_scope 非法（${r.dataScope}）：${r.code}（只允许 ${DATA_SCOPES.join(' / ')}）`);
    }
    if (!r.name) errors.push(`角色名缺失：${r.code}`);
  }
  const missingRoles = ROLE_WHITELIST.filter((c) => !roleByCode.has(c));
  if (missingRoles.length) {
    errors.push(`9 个内置角色未全部播种，缺：${missingRoles.join(', ')}`);
  }

  // --- 0b：角色段必须先于任何 sys_role_permission 插入（也先于权限树） ----------
  if (marks.firstRole < 0) {
    errors.push('未解析到任何 sys_role 的 INSERT（角色播种缺失）');
  } else {
    if (marks.firstGrant >= 0 && marks.firstRole > marks.firstGrant) {
      errors.push(
        `顺序错误：角色播种段（偏移 ${marks.firstRole}）出现在 sys_role_permission 授权段（偏移 ${marks.firstGrant}）之后；` +
          '授权 JOIN 不到角色会静默插入 0 行',
      );
    }
    if (marks.firstPermission >= 0 && marks.firstRole > marks.firstPermission) {
      errors.push(
        `顺序错误：角色播种段（偏移 ${marks.firstRole}）出现在 sys_permission 权限树段（偏移 ${marks.firstPermission}）之后`,
      );
    }
  }

  // --- 1/2/3：code 唯一、perm_type 白名单、菜单 url ---------------------------
  const byCode = new Map();
  for (const p of permissions) {
    if (p.code == null) {
      errors.push('存在无法解析 code 的权限行');
      continue;
    }
    if (byCode.has(p.code)) {
      errors.push(`权限码重复：${p.code}`);
      continue;
    }
    byCode.set(p.code, p);

    if (!PERM_TYPES.includes(p.type)) {
      errors.push(`perm_type 非法（${p.type}）：${p.code}（只允许 ${PERM_TYPES.join(' / ')}）`);
    }
    if (p.type === 'menu' && p.url != null && !p.url.startsWith('/')) {
      errors.push(`菜单 url 若非空须以 / 开头：${p.code} -> ${p.url}`);
    }
    if (p.code.length > CODE_MAX) errors.push(`权限码超过 ${CODE_MAX} 字符：${p.code}`);
    if (p.name == null || p.name === '') errors.push(`权限名缺失：${p.code}`);
    else if (p.name.length > NAME_MAX) errors.push(`权限名超过 ${NAME_MAX} 字符：${p.code}`);
    if (p.url != null && p.url.length > URL_MAX) errors.push(`url 超过 ${URL_MAX} 字符：${p.code}`);
    if (!Number.isInteger(p.sortNo)) errors.push(`sort_no 不是整数：${p.code}`);
    if (p.code.startsWith('flow:') && p.type !== 'button') {
      warnings.push(`flow:* 约定为按钮：${p.code} 的 perm_type = ${p.type}`);
    }
  }

  // --- 4：父 code 必须在本次插入集合内（顶层 NULL） ---------------------------
  for (const p of permissions) {
    if (p.parentCode != null && !byCode.has(p.parentCode)) {
      errors.push(`父权限码不在本次插入集合内：${p.code} -> ${p.parentCode}`);
    }
    if (p.parentCode === p.code) errors.push(`自引用父节点：${p.code}`);
  }

  // --- 5：父先于子（按文件解析顺序） -----------------------------------------
  const appeared = new Set();
  for (const p of permissions) {
    if (p.parentCode != null && !appeared.has(p.parentCode)) {
      errors.push(`父先于子被破坏：${p.code} 出现时其父 ${p.parentCode} 尚未插入`);
    }
    appeared.add(p.code);
  }

  // --- 6：同一父下 sort_no 不重复 --------------------------------------------
  const sortSeen = new Map();
  for (const p of permissions) {
    const key = `${p.parentCode == null ? '<root>' : p.parentCode}#${p.sortNo}`;
    if (sortSeen.has(key)) {
      errors.push(`同一父下 sort_no 重复：${sortSeen.get(key)} 与 ${p.code}（父=${p.parentCode == null ? 'NULL' : p.parentCode}，sort_no=${p.sortNo}）`);
    } else {
      sortSeen.set(key, p.code);
    }
  }

  // --- 4b：父链可达根（无环 / 无悬空） ---------------------------------------
  const parentOf = new Map();
  for (const p of permissions) parentOf.set(p.code, p.parentCode);
  const depthCache = new Map();
  function depthOf(code, trail) {
    if (depthCache.has(code)) return depthCache.get(code);
    if (trail.has(code)) {
      errors.push(`权限树存在环：${[...trail, code].join(' -> ')}`);
      return 1;
    }
    const parent = parentOf.get(code);
    let d = 1;
    if (parent != null) {
      trail.add(code);
      d = depthOf(parent, trail) + 1;
      trail.delete(code);
    }
    depthCache.set(code, d);
    return d;
  }

  // --- 7/8/9：角色授权 --------------------------------------------------------
  const grantedByRole = new Map();
  let grantStatementCount = grants.length;
  for (const g of grants) {
    if (!ROLE_WHITELIST.includes(g.role)) {
      errors.push(`角色码不在 9 码白名单内：${g.role}`);
    }
    if (g.codes.length === 0) {
      warnings.push(`角色 ${g.role} 的某条授权语句未解析出任何权限码`);
    }
    if (!grantedByRole.has(g.role)) grantedByRole.set(g.role, new Set());
    const set = grantedByRole.get(g.role);
    for (const c of g.codes) {
      if (!byCode.has(c)) {
        errors.push(`角色授权引用了未定义的权限码：${g.role} -> ${c}`);
        continue;
      }
      set.add(c);
    }
  }

  // 8：admin 必须拿到全部权限
  const allCodes = [...byCode.keys()];
  const adminSet = grantedByRole.get('admin') || new Set();
  const missingForAdmin = allCodes.filter((c) => !adminSet.has(c));
  if (missingForAdmin.length) {
    errors.push(`admin 未被授予全部权限，缺 ${missingForAdmin.length} 项：${missingForAdmin.join(', ')}`);
  }

  // 9：company_admin 越权防护
  const caSet = grantedByRole.get('company_admin');
  if (!caSet) {
    errors.push('company_admin 没有任何授权语句（该角色默认授权缺失）');
  } else {
    const violations = [];
    for (const c of caSet) {
      if (COMPANY_ADMIN_FORBIDDEN_EXACT.includes(c)) violations.push(c);
      else if (COMPANY_ADMIN_FORBIDDEN_PREFIX.some((pre) => c.startsWith(pre))) violations.push(c);
      else if (c === COMPANY_ADMIN_FORBIDDEN_ROLE_BRANCH || c.startsWith(`${COMPANY_ADMIN_FORBIDDEN_ROLE_BRANCH}:`)) {
        violations.push(c);
      }
    }
    if (violations.length) {
      errors.push(`company_admin 越权（不可再授权）：不应包含 ${violations.sort().join(', ')}`);
    }
  }

  // 每个角色码都应至少有一条授权语句
  for (const role of ROLE_WHITELIST) {
    if (!grantedByRole.has(role)) errors.push(`缺失角色默认授权：${role}`);
  }

  // --- 9b：employee 必须持有 撤回 + H5，且不得持有任何审批动作 ------------------
  const employeeSet = grantedByRole.get('employee');
  if (employeeSet) {
    for (const code of EMPLOYEE_REQUIRED) {
      if (!byCode.has(code)) {
        errors.push(`employee 必备权限码在权限树中不存在：${code}`);
      } else if (!employeeSet.has(code)) {
        errors.push(`employee 缺少必备权限项：${code}`);
      }
    }
    const approverActions = [...employeeSet].filter(
      (c) => c.startsWith('flow:') && c !== 'flow:task:withdraw',
    );
    if (approverActions.length) {
      errors.push(`employee 越权：不应持有审批动作 ${approverActions.sort().join(', ')}`);
    }
  }

  // --- 9c：group_leader / chairman 必须有集团层报表查看、且不含报表导出 ---------
  for (const role of GROUP_REPORT_ROLES) {
    const set = grantedByRole.get(role);
    if (!set) continue; // 缺失授权已在上面报过
    if (!set.has(GROUP_REPORT_NODE)) {
      errors.push(`${role} 缺少集团层报表菜单节点：${GROUP_REPORT_NODE}`);
    }
    const viewItems = [...set].filter(
      (c) => c.startsWith(`${GROUP_REPORT_NODE}:`) && c !== GROUP_REPORT_EXPORT,
    );
    if (viewItems.length === 0) {
      errors.push(`${role} 缺少集团层报表查看项（${GROUP_REPORT_NODE}:*）`);
    }
    if (set.has(GROUP_REPORT_EXPORT)) {
      errors.push(`${role} 越权：不应持有 ${GROUP_REPORT_EXPORT}（报表导出仅 admin）`);
    }
  }

  // --- 9d：仅 admin 权限项不得下放 --------------------------------------------
  for (const code of ADMIN_ONLY_CODES) {
    if (!byCode.has(code)) {
      errors.push(`仅 admin 权限项在权限树中不存在：${code}`);
      continue;
    }
    for (const [role, set] of grantedByRole) {
      if (role !== 'admin' && set.has(code)) {
        errors.push(`仅 admin 权限项被下放：${role} -> ${code}`);
      }
    }
  }

  // --- 9e：祖先闭包（PermissionTreePolicy.requireAncestorClosed 同口径） -------
  // 「父节点未授予时子节点不得单独授予」——服务端对违规集合一律 400，故种子必须同口径。
  for (const [role, set] of grantedByRole) {
    const violations = [];
    for (const code of set) {
      const node = byCode.get(code);
      const parent = node ? node.parentCode : null;
      if (parent != null && !set.has(parent)) violations.push(`${code} 缺父 ${parent}`);
    }
    if (violations.length) {
      errors.push(`角色 ${role} 违反祖先闭包（子被授予而父未授予）：${violations.sort().join('；')}`);
    }
  }

  // --- 10：统计 --------------------------------------------------------------
  const depthDistribution = {};
  let maxDepth = 0;
  for (const p of permissions) {
    const d = depthOf(p.code, new Set());
    p.depth = d;
    depthDistribution[d] = (depthDistribution[d] || 0) + 1;
    if (d > maxDepth) maxDepth = d;
  }

  const grantedPerRole = {};
  let grantRows = 0;
  for (const role of ROLE_WHITELIST) {
    const n = (grantedByRole.get(role) || new Set()).size;
    grantedPerRole[role] = n;
    grantRows += n;
  }

  const ungranted = allCodes.filter(
    (c) => ![...grantedByRole.values()].some((set) => set.has(c)),
  );
  if (ungranted.length) {
    warnings.push(`以下权限码未被任何角色授予：${ungranted.join(', ')}`);
  }

  if (permissions.length < SIZE_MIN || permissions.length > SIZE_MAX) {
    warnings.push(
      `权限项共 ${permissions.length} 项，超出任务书建议区间 ${SIZE_MIN}–${SIZE_MAX}：任务书「至少覆盖」清单展开即需 ${permissions.length} 行。**已裁定接受**，不再删减`,
    );
  }

  const stats = {
    rolesSeeded: roles.length,
    roleCodes: ROLE_WHITELIST.filter((c) => roleByCode.has(c)),
    permissionRows: permissions.length,
    menus: permissions.filter((p) => p.type === 'menu').length,
    buttons: permissions.filter((p) => p.type === 'button').length,
    apis: permissions.filter((p) => p.type === 'api').length,
    topLevel: permissions.filter((p) => p.parentCode == null).length,
    maxDepth,
    depthDistribution,
    grantStatements: grantStatementCount,
    grantRows,
    rolesWithGrants: grantedByRole.size,
    grantedPerRole,
    adminOnlyCodes: ADMIN_ONLY_CODES,
    sectionOrder: {
      firstRoleInsert: marks.firstRole,
      firstPermissionInsert: marks.firstPermission,
      firstGrantInsert: marks.firstGrant,
      rolesBeforeGrants: marks.firstRole >= 0 && marks.firstGrant >= 0 && marks.firstRole < marks.firstGrant,
    },
  };

  return stats;
}

// --- main -------------------------------------------------------------------

function main() {
  const target = process.argv[2] || DEFAULT_FILE;
  const file = path.resolve(process.cwd(), target);
  const errors = [];
  const warnings = [];

  if (!fs.existsSync(file)) {
    process.stdout.write(
      `${JSON.stringify(
        { ok: false, file: target, stats: null, errors: [`文件不存在：${file}`], warnings: [] },
        null,
        2,
      )}\n`,
    );
    return 1;
  }

  const text = stripBom(fs.readFileSync(file, 'utf8'));
  const stats = check(text, errors, warnings);

  const report = {
    ok: errors.length === 0,
    file: path.relative(process.cwd(), file).split(path.sep).join('/'),
    stats,
    errors,
    warnings,
  };
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
  return errors.length === 0 ? 0 : 1;
}

process.exitCode = main();
