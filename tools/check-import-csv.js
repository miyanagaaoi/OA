#!/usr/bin/env node
/**
 * 组织与人员批量导入模板 · CSV 校验器（零依赖）
 *
 * 用法：node tools/check-import-csv.js oa-deploy/import
 *
 * 校验范围（对应 doc/import-spec.md §4 校验规则清单）：
 *   1. 文件存在性、UTF-8 BOM、UTF-8 可解码（无替换字符 U+FFFD）
 *   2. 表头列名与规格**逐字逐序**完全匹配
 *   3. 数据行数（模板要求 3–5 行示例）、列数齐整、无空行
 *   4. 枚举取值：org_type / status / leader_type / is_primary / business_line
 *   5. org.csv：org_path 唯一、parent_path 存在且结构一致、父子类型合法、集团唯一
 *   6. user.csv：account 唯一与格式、employee_no 必填唯一（≤32）与格式、phone 格式、
 *      company_path / dept_path 可解析且归属一致
 *   7. org_leader.csv：org_path / user_account 可解析、同一组织同一业务线正职唯一
 *   8. user_position.csv：(user_account, org_path) 唯一、每人最多一个 is_primary=是
 *   9. user_role.csv：user_account 可解析、role_code 属于已初始化角色集、
 *      scope_org_path 可解析、(user_account, role_code, scope_org_path) 唯一
 *
 * 输出：JSON 校验报告（stdout）。退出码：0 = 无 error；1 = 存在 error；2 = 用法/路径错误。
 * 只读工具：不修改任何导入文件。
 */
'use strict';

const fs = require('fs');
const path = require('path');

const TOOL = 'check-import-csv';
const VERSION = '1.3.0';
const SAMPLE_MIN = 3;
const SAMPLE_MAX = 5;

// ---------------------------------------------------------------------------
// 规格定义（列名必须与 doc/import-spec.md §3 的表头定义逐字一致）
// ---------------------------------------------------------------------------
const FILES = [
  {
    name: 'org.csv',
    title: '组织架构',
    table: 'sys_org',
    columns: ['org_path', 'org_name', 'org_type', 'parent_path', 'status', 'remark'],
  },
  {
    name: 'user.csv',
    title: '人员',
    table: 'sys_user',
    columns: ['account', 'employee_no', 'name', 'phone', 'email', 'company_path', 'dept_path', 'status', 'remark'],
  },
  {
    name: 'org_leader.csv',
    title: '组织负责人',
    table: 'sys_org_leader',
    columns: ['org_path', 'user_account', 'leader_type', 'sort', 'business_line', 'remark'],
  },
  {
    name: 'user_position.csv',
    title: '岗位任职（一人多岗）',
    table: 'sys_user_position',
    columns: ['user_account', 'org_path', 'post_name', 'is_primary', 'remark'],
  },
  {
    name: 'user_role.csv',
    title: '角色分配',
    table: 'sys_user_role',
    columns: ['user_account', 'role_code', 'scope_org_path', 'remark'],
  },
];

// 已初始化角色集（白名单）：**以 doc/data-model.md 3.1 `sys_role.code` 列注释为权威源**（9 个码），
// 可用环境变量 IMPORT_ROLE_CODES=a,b,c 覆盖（实施时若角色码调整，以 sys_role 初始化脚本为准）。
const DEFAULT_ROLE_CODES = [
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
const ROLE_NAMES = {
  admin: '系统管理员',
  company_admin: '分公司流程管理员',
  employee: '普通员工',
  dept_leader: '部门/科室负责人',
  branch_leader: '分公司分管领导',
  subsidiary_gm: '子公司总经理',
  finance_owner: '集团归口（财务部）负责人',
  group_leader: '集团分管领导',
  chairman: '集团董事长',
};
const ROLE_CODES = process.env.IMPORT_ROLE_CODES
  ? process.env.IMPORT_ROLE_CODES.split(',').map((s) => s.trim()).filter(Boolean)
  : DEFAULT_ROLE_CODES;
// 各角色的数据域建议（对应 sys_role.data_scope 的 CHECK 值域，见规格书 §2.3）
const ROLE_DATA_SCOPE = {
  admin: 'group_all',
  company_admin: 'company',
  employee: 'self',
  dept_leader: 'dept',
  branch_leader: 'company',
  subsidiary_gm: 'company',
  finance_owner: 'group_category',
  group_leader: 'group_category',
  chairman: 'group_all',
};
const ROLE_CODE_RE = /^[a-z][a-z0-9_]{1,31}$/;

const ENUM = {
  org_type: ['集团', '公司', '部门', '科室'],
  org_status: ['启用', '停用'],
  user_status: ['在职', '离职'],
  leader_type: ['正职', '副职'],
  is_primary: ['是', '否'],
  // 业务线直接复用事项类别五值的中文标签（enums.md §10.1）；
  // 口径：财务分管领导在系统中等同于「经济」类分管领导（Q10 后五个事项类别统一归口财务部）
  business_line: ['经营', '经济', '行政', '人力', '投资'],
  // 父节点允许的组织类型（集团→公司→部门→科室；部门可直属集团，见 PRD 5.1 集团职能部门）
  parent_of: { 集团: [], 公司: ['集团'], 部门: ['集团', '公司'], 科室: ['部门'] },
};

const ACCOUNT_RE = /^[A-Za-z][A-Za-z0-9_.-]{7,63}$/;
const EMPLOYEE_NO_RE = /^[A-Za-z0-9-]{1,32}$/;
const PHONE_RE = /^1[3-9]\d{9}$/;
const EMAIL_RE = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;

const FIX = {
  'E-FILE-001': '确认四个模板文件均已提交到导入目录，文件名与大小写完全一致',
  'E-ENC-001': '用「另存为 → CSV UTF-8（逗号分隔）」重新导出，或补写 EF BB BF 三字节 BOM',
  'E-ENC-002': '以真正的 UTF-8 重新编码保存（禁止 GBK / ANSI / UTF-16），中文不得出现替换字符',
  'E-HDR-001': '首行表头改为规格定义的列名与顺序，不得改名、增列、减列或调整次序',
  'E-HDR-002': '补写首行表头',
  'E-ROW-001': '每行列数与表头一致；值中含逗号时用英文双引号包裹',
  'E-ROW-002': `数据行数（不含表头）调整为 ${SAMPLE_MIN}–${SAMPLE_MAX} 行示例数据`,
  'E-ROW-003': '删除表格中的空行（含末尾多余空行）',
  'E-ROW-004': '删除与表头重复的数据行',
  'E-ORG-001': 'org_path 是全库唯一业务键，重复项请合并或改名后重导',
  'E-ORG-002': '先导入其父组织行，或修正拼写；父组织不存在时本行必定失败',
  'E-ORG-003': 'parent_path 必须等于 org_path 去掉最后一段（例：集团/公司A/财务部 → 集团/公司A）',
  'E-ORG-004': '集团根节点行的 parent_path 必须留空',
  'E-ORG-005': '非集团行必须填写 parent_path',
  'E-ORG-006': '层级固定为 集团→公司→部门→科室；公司必须挂在集团下，科室必须挂在部门下',
  'E-ORG-007': `org_type 必填且只能取 ${ENUM.org_type.join(' / ')}`,
  'E-ORG-008': 'org_path 必填，不得以 / 开头或结尾、不得出现连续 //，各段由 / 分隔，总长 ≤255',
  'E-ORG-009': `status 必填且只能取 ${ENUM.org_status.join(' / ')}`,
  'E-ORG-012': 'org_name 必填，且不超过 100 字符',
  'E-ORG-013': 'remark 不超过 255 字符（sys_org.remark VARCHAR(255)）',
  'E-ORG-015': '整棵组织树有且仅有一个集团根节点',
  'W-ORG-016': '组织停用前必须清空其在途单据（PRD 5.5），请确认已无在途',
  'W-ORG-017': '停用组织下仍有在职人员，请先转岗或改派',
  'E-USER-001': 'account 必填，8–64 位、字母开头，仅含字母/数字/下划线/点/连字符',
  'E-USER-002': 'account 是登录名主键（sys_user.account 唯一），重复行请合并',
  'E-USER-014': 'employee_no 必填，1–32 字符，仅含字母、数字与 -（例 A0001）',
  'E-USER-015': 'employee_no 是水印（姓名+工号，REQ-USER-004 / AC-44）与人事核对的依据，必须全文件唯一',
  'E-USER-003': 'phone 必填且为 11 位大陆手机号（1 开头，第二位 3–9）',
  'E-USER-004': 'email 格式形如 name@example.com，不超过 128 字符；无邮箱请留空',
  'E-USER-005': 'company_path 必填且必须是 org.csv 中已存在的组织路径',
  'E-USER-006': 'dept_path 必须能在 org.csv 中找到且为部门/科室；不挂部门请留空',
  'E-USER-007': `status 必填且只能取 ${ENUM.user_status.join(' / ')}（停用状态不通过批量导入设置）`,
  'E-USER-008': 'company_path 只能指向「公司」节点；集团本部人员填「集团」',
  'E-USER-010': 'name 必填，且不超过 50 字符',
  'E-USER-011': 'dept_path 必须位于 company_path 之下（等于它或以它加 / 开头）',
  'E-USER-012': 'remark 不超过 255 字符（sys_user.remark VARCHAR(255)）',
  'E-LEAD-001': 'org_path 必填且必须能在 org.csv 中找到',
  'E-LEAD-002': 'user_account 必填且必须能在 user.csv 中找到',
  'E-LEAD-003': `leader_type 必填且只能取 ${ENUM.leader_type.join(' / ')}`,
  'E-LEAD-004': '同一组织同一业务线只能有一个正职；多副职允许，业务线留空视为同一组',
  'E-LEAD-005': `business_line 为事项类别五值中文标签，只能取 ${ENUM.business_line.join(' / ')} 或留空`,
  'E-LEAD-006': '离职人员不得作为组织负责人（审批人解析会取到无效候选人）',
  'E-LEAD-007': 'sort 为 0–9999 的整数，留空按 0 处理',
  'E-LEAD-008': 'business_line 仅在「集团」节点上填写（PRD 5.1 集团层按业务线绑定分管领导）',
  'E-LEAD-009': '(org_path, user_account, leader_type, business_line) 完全重复',
  'E-POS-001': 'org_path 必填且必须能在 org.csv 中找到',
  'E-POS-002': 'user_account 必填且必须能在 user.csv 中找到',
  'E-POS-003': `is_primary 必填且只能取 ${ENUM.is_primary.join(' / ')}`,
  'E-POS-004': '(user_account, org_path) 是 sys_user_position 唯一键（uk_user_org），不得重复',
  'E-POS-005': '每人最多一条 is_primary=是（主岗唯一）',
  'E-POS-006': 'post_name 必填，且不超过 50 字符',
  'E-POS-007': 'remark 不超过 255 字符（sys_user_position.remark VARCHAR(255)）',
  'E-LEAD-010': 'remark 不超过 255 字符（sys_org_leader.remark VARCHAR(255)）',
  'E-ROLE-001': `role_code 必须属于已初始化角色集（唯一权威源：doc/data-model.md 3.1 sys_role.code）——${ROLE_CODES.map((c) => (ROLE_NAMES[c] ? `${c}（${ROLE_NAMES[c]}）` : c)).join(' / ')}；可用环境变量 IMPORT_ROLE_CODES 覆盖`,
  'E-ROLE-002': 'user_account 必填且必须能在 user.csv 中找到',
  'E-ROLE-003': '同一 user + role + 空数据域视为同一条：uk_sys_user_role 为 (user_id, role_id, scope_org_key)，scope_org_id 为 NULL 时按 0 参与唯一键（导入校验与库约束一致）',
  'E-ROLE-004': 'scope_org_path 必须能在 org.csv 中找到；为空表示按角色默认数据域',
  'E-ROLE-005': 'remark 不超过 255 字符（sys_user_role.remark VARCHAR(255)）',
  'W-POS-008': '建议为每人的 dept_path 补一条岗位记录，保证通讯录与岗位表一致',
  'W-POS-009': '岗位组织不在该员工所属公司子树内，请确认是否为跨公司兼职',
  'W-FMT-001': '单元格首尾存在空格，导入程序会按 trim 处理；建议在源文件中清理',
};

// ---------------------------------------------------------------------------
// 工具函数
// ---------------------------------------------------------------------------
const findings = [];
function push(severity, code, file, line, column, value, message) {
  findings.push({
    severity,
    code,
    file: file || null,
    line: line === null || line === undefined ? null : line,
    column: column || null,
    value: value === null || value === undefined ? null : String(value),
    message,
    suggestion: FIX[code] || null,
  });
}
const err = (...a) => push('error', ...a);
const warn = (...a) => push('warning', ...a);

function readRaw(file) {
  const buf = fs.readFileSync(file);
  const hasUtf8Bom = buf.length >= 3 && buf[0] === 0xef && buf[1] === 0xbb && buf[2] === 0xbf;
  const hasUtf16Bom =
    buf.length >= 2 && ((buf[0] === 0xff && buf[1] === 0xfe) || (buf[0] === 0xfe && buf[1] === 0xff));
  const body = hasUtf8Bom ? buf.subarray(3) : buf;
  return { hasUtf8Bom, hasUtf16Bom, text: body.toString('utf8') };
}

/** RFC4180 风格解析：支持双引号包裹与 "" 转义；行尾 \n 或 \r\n */
function parseCsv(text) {
  const rows = [];
  let row = [];
  let cell = '';
  let inQuotes = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (inQuotes) {
      if (c === '"') {
        if (text[i + 1] === '"') {
          cell += '"';
          i++;
        } else {
          inQuotes = false;
        }
      } else {
        cell += c;
      }
    } else if (c === '"') {
      inQuotes = true;
    } else if (c === ',') {
      row.push(cell);
      cell = '';
    } else if (c === '\n') {
      row.push(cell);
      rows.push(row);
      row = [];
      cell = '';
    } else if (c !== '\r') {
      cell += c;
    }
  }
  if (cell !== '' || row.length > 0) {
    row.push(cell);
    rows.push(row);
  }
  return rows;
}

function loadTable(spec, dir) {
  const file = path.join(dir, spec.name);
  const rel = spec.name;
  const report = {
    file: rel,
    title: spec.title,
    table: spec.table,
    exists: false,
    utf8Bom: false,
    encodingOk: false,
    header: [],
    expectedHeader: spec.columns.slice(),
    headerOk: false,
    dataRows: 0,
    emptyRows: 0,
    columns: spec.columns.length,
    rows: [],
  };
  if (!fs.existsSync(file)) {
    err('E-FILE-001', rel, null, null, null, `模板文件不存在：${file}`);
    return report;
  }
  report.exists = true;

  const raw = readRaw(file);
  report.utf8Bom = raw.hasUtf8Bom;
  if (raw.hasUtf16Bom) {
    err('E-ENC-002', rel, null, null, null, '检测到 UTF-16 BOM，模板必须为 UTF-8 带 BOM');
  } else if (!raw.hasUtf8Bom) {
    err('E-ENC-001', rel, null, null, null, '缺少 UTF-8 BOM（EF BB BF），Excel 直接打开会乱码');
  }
  const bad = raw.text.indexOf('\uFFFD');
  if (bad >= 0) {
    const line = raw.text.slice(0, bad).split('\n').length;
    err('E-ENC-002', rel, line, null, null, '文件不是合法 UTF-8（含替换字符 U+FFFD）');
  }
  report.encodingOk = !raw.hasUtf16Bom && raw.hasUtf8Bom && bad < 0;

  const parsed = parseCsv(raw.text);
  if (parsed.length === 0 || parsed[0].every((c) => c.trim() === '')) {
    err('E-HDR-002', rel, 1, null, null, '缺少表头行');
    return report;
  }

  report.header = parsed[0].map((c) => c.trim());
  report.headerOk =
    report.header.length === spec.columns.length && report.header.every((c, i) => c === spec.columns[i]);
  if (!report.headerOk) {
    const missing = spec.columns.filter((c) => !report.header.includes(c));
    const extra = report.header.filter((c) => !spec.columns.includes(c) && c !== '');
    err(
      'E-HDR-001',
      rel,
      1,
      null,
      report.header.join(','),
      `表头与规格不一致；期望 [${spec.columns.join(',')}]；缺列 [${missing.join(',') || '无'}]；多列 [${extra.join(',') || '无'}]`,
    );
  }

  const width = report.header.length;
  for (let i = 1; i < parsed.length; i++) {
    const cells = parsed[i];
    const lineNo = i + 1;
    if (cells.every((c) => c.trim() === '')) {
      report.emptyRows++;
      err('E-ROW-003', rel, lineNo, null, null, '存在空行');
      continue;
    }
    if (cells.length !== width) {
      err('E-ROW-001', rel, lineNo, null, cells.join(','), `列数为 ${cells.length}，表头为 ${width} 列`);
    }
    const obj = { __line: lineNo, __cells: cells.map((c) => c.trim()) };
    spec.columns.forEach((col, idx) => {
      const rawCell = cells[idx] === undefined ? '' : cells[idx];
      obj[col] = rawCell.trim();
      if (rawCell !== obj[col] && obj[col] !== '') {
        warn('W-FMT-001', rel, lineNo, col, rawCell, `「${col}」首尾含空格`);
      }
    });
    if (obj.__cells.every((c, idx) => c === report.header[idx])) {
      err('E-ROW-004', rel, lineNo, null, cells.join(','), '数据区存在与表头完全相同的行（表头被重复）');
    }
    report.rows.push(obj);
  }
  report.dataRows = report.rows.length;

  if (report.dataRows < SAMPLE_MIN || report.dataRows > SAMPLE_MAX) {
    err(
      'E-ROW-002',
      rel,
      null,
      null,
      String(report.dataRows),
      `数据行数为 ${report.dataRows}，模板要求 ${SAMPLE_MIN}–${SAMPLE_MAX} 行示例数据`,
    );
  }
  return report;
}

function enumCheck(rel, row, column, value, allowed, code, required) {
  if (value === '') {
    if (required) err(code, rel, row.__line, column, '', `「${column}」为必填列，取值不得为空`);
    return;
  }
  if (!allowed.includes(value)) {
    err(code, rel, row.__line, column, value, `「${column}」取值非法：${value}，允许值 ${allowed.join(' / ')}`);
  }
}

// ---------------------------------------------------------------------------
// org.csv
// ---------------------------------------------------------------------------
function checkOrg(rel, t) {
  const byPath = new Map();
  for (const r of t.rows) {
    if (!r.org_path) {
      err('E-ORG-008', rel, r.__line, 'org_path', '', 'org_path 为空');
    } else {
      const segments = r.org_path.split('/');
      if (r.org_path.startsWith('/') || r.org_path.endsWith('/') || segments.some((s) => !s)) {
        err('E-ORG-008', rel, r.__line, 'org_path', r.org_path, 'org_path 含空段或以 / 开头/结尾');
      }
      if (r.org_path.length > 255) {
        err('E-ORG-008', rel, r.__line, 'org_path', r.org_path, 'org_path 超过 255 字符（sys_org.path 上限）');
      }
      if (byPath.has(r.org_path)) {
        err('E-ORG-001', rel, r.__line, 'org_path', r.org_path, `org_path 重复（首次出现于第 ${byPath.get(r.org_path).__line} 行）`);
      } else {
        byPath.set(r.org_path, r);
      }
    }
    if (!r.org_name) err('E-ORG-012', rel, r.__line, 'org_name', '', 'org_name 为空');
    else if ([...r.org_name].length > 100) err('E-ORG-012', rel, r.__line, 'org_name', r.org_name, `org_name 长度 ${[...r.org_name].length} > 100`);
    if ([...r.remark].length > 255) err('E-ORG-013', rel, r.__line, 'remark', r.remark, `remark 长度 ${[...r.remark].length} > 255（sys_org.remark VARCHAR(255)）`);
    enumCheck(rel, r, 'org_type', r.org_type, ENUM.org_type, 'E-ORG-007', true);
    enumCheck(rel, r, 'status', r.status, ENUM.org_status, 'E-ORG-009', true);
  }

  const roots = t.rows.filter((r) => r.org_type === '集团');
  if (roots.length !== 1) {
    err(
      'E-ORG-015',
      rel,
      roots[0] ? roots[0].__line : null,
      'org_type',
      String(roots.length),
      `集团根节点数量为 ${roots.length}，必须恰好 1 个`,
    );
  }

  for (const r of t.rows) {
    if (!r.org_path || !byPath.has(r.org_path)) continue;
    if (r.org_type === '集团') {
      if (r.parent_path !== '') err('E-ORG-004', rel, r.__line, 'parent_path', r.parent_path, '集团行的 parent_path 必须为空');
      continue;
    }
    if (r.parent_path === '') {
      err('E-ORG-005', rel, r.__line, 'parent_path', '', `org_type=${r.org_type} 的行必须填写 parent_path`);
      continue;
    }
    const expectedParent = r.org_path.split('/').slice(0, -1).join('/');
    if (r.parent_path !== expectedParent) {
      err('E-ORG-003', rel, r.__line, 'parent_path', r.parent_path, `与 org_path 结构不一致，应为「${expectedParent}」`);
    }
    const parent = byPath.get(r.parent_path);
    if (!parent) {
      err('E-ORG-002', rel, r.__line, 'parent_path', r.parent_path, '父路径在本文件内不存在');
      continue;
    }
    const allowed = ENUM.parent_of[r.org_type] || [];
    if (parent.org_type && !allowed.includes(parent.org_type)) {
      err(
        'E-ORG-006',
        rel,
        r.__line,
        'org_type',
        `${r.org_type} 挂在 ${parent.org_type} 下`,
        `层级非法：${r.org_type} 不能挂在 ${parent.org_type}（${r.parent_path}）下，允许的父类型为 ${allowed.join('/') || '无'}`,
      );
    }
    if (parent.status === '停用') {
      warn('W-ORG-016', rel, r.__line, 'parent_path', r.parent_path, `父组织「${r.parent_path}」已停用，本节点导入后不可作为发起归属`);
    }
  }
  return byPath;
}

// ---------------------------------------------------------------------------
// user.csv
// ---------------------------------------------------------------------------
function checkUser(rel, t, orgByPath) {
  const byAccount = new Map();
  const byEmployeeNo = new Map();
  for (const r of t.rows) {
    if (!r.account) {
      err('E-USER-001', rel, r.__line, 'account', '', 'account 为空');
    } else if (!ACCOUNT_RE.test(r.account)) {
      err('E-USER-001', rel, r.__line, 'account', r.account, '格式非法：需 8–64 位、字母开头，仅含字母/数字/下划线/点/连字符');
    }
    if (byAccount.has(r.account)) {
      err('E-USER-002', rel, r.__line, 'account', r.account, `账号重复（首次出现于第 ${byAccount.get(r.account).__line} 行）`);
    } else if (r.account) {
      byAccount.set(r.account, r);
    }

    // 工号：sys_user.employee_no（水印「姓名 + 工号」，REQ-USER-004 / AC-44）——必填、唯一、≤32
    if (!r.employee_no) {
      err('E-USER-014', rel, r.__line, 'employee_no', '', 'employee_no 为空（工号必填，水印依赖该列）');
    } else if (!EMPLOYEE_NO_RE.test(r.employee_no)) {
      err('E-USER-014', rel, r.__line, 'employee_no', r.employee_no, '格式非法：需 1–32 字符，仅含字母/数字/连字符');
    }
    if (byEmployeeNo.has(r.employee_no)) {
      err('E-USER-015', rel, r.__line, 'employee_no', r.employee_no, `工号重复（首次出现于第 ${byEmployeeNo.get(r.employee_no).__line} 行）`);
    } else if (r.employee_no) {
      byEmployeeNo.set(r.employee_no, r);
    }

    if (!r.name) err('E-USER-010', rel, r.__line, 'name', '', 'name 为空');
    else if ([...r.name].length > 50) err('E-USER-010', rel, r.__line, 'name', r.name, 'name 超过 50 字符');

    if (!r.phone) {
      err('E-USER-003', rel, r.__line, 'phone', '', 'phone 为空（T-04 已定稿：phone 必填）');
    } else if (!PHONE_RE.test(r.phone)) {
      err('E-USER-003', rel, r.__line, 'phone', r.phone, '手机号格式非法（应为 11 位大陆手机号）');
    }
    if (r.email !== '' && (!EMAIL_RE.test(r.email) || r.email.length > 128)) {
      err('E-USER-004', rel, r.__line, 'email', r.email, '邮箱格式非法或超过 128 字符');
    }
    enumCheck(rel, r, 'status', r.status, ENUM.user_status, 'E-USER-007', true);
    if ([...r.remark].length > 255) err('E-USER-012', rel, r.__line, 'remark', r.remark, `remark 长度 ${[...r.remark].length} > 255（sys_user.remark VARCHAR(255)）`);

    const company = r.company_path ? orgByPath.get(r.company_path) : null;
    if (!r.company_path) {
      err('E-USER-005', rel, r.__line, 'company_path', '', 'company_path 为空');
    } else if (!company) {
      err('E-USER-005', rel, r.__line, 'company_path', r.company_path, 'company_path 在 org.csv 中不存在');
    } else if (company.org_type !== '公司' && company.org_type !== '集团') {
      err('E-USER-008', rel, r.__line, 'company_path', r.company_path, `company_path 指向 ${company.org_type}（${company.org_name}），只允许「公司」或「集团」`);
    }

    if (r.dept_path !== '') {
      const dept = orgByPath.get(r.dept_path);
      if (!dept) {
        err('E-USER-006', rel, r.__line, 'dept_path', r.dept_path, 'dept_path 在 org.csv 中不存在');
      } else {
        if (dept.org_type !== '部门' && dept.org_type !== '科室') {
          err('E-USER-006', rel, r.__line, 'dept_path', r.dept_path, `dept_path 指向 ${dept.org_type}，应为「部门」或「科室」`);
        }
        if (r.company_path && !(r.dept_path === r.company_path || r.dept_path.startsWith(r.company_path + '/'))) {
          err('E-USER-011', rel, r.__line, 'dept_path', r.dept_path, `dept_path 不在 company_path「${r.company_path}」子树内`);
        }
      }
    }
  }
  return byAccount;
}

// ---------------------------------------------------------------------------
// org_leader.csv
// ---------------------------------------------------------------------------
function checkLeader(rel, t, orgByPath, userByAccount) {
  const seen = new Set();
  const primaryByGroup = new Map();
  for (const r of t.rows) {
    const org = r.org_path ? orgByPath.get(r.org_path) : null;
    if (!r.org_path) err('E-LEAD-001', rel, r.__line, 'org_path', '', 'org_path 为空');
    else if (!org) err('E-LEAD-001', rel, r.__line, 'org_path', r.org_path, 'org_path 在 org.csv 中不存在');

    const user = r.user_account ? userByAccount.get(r.user_account) : null;
    if (!r.user_account) err('E-LEAD-002', rel, r.__line, 'user_account', '', 'user_account 为空');
    else if (!user) err('E-LEAD-002', rel, r.__line, 'user_account', r.user_account, 'user_account 在 user.csv 中不存在');

    enumCheck(rel, r, 'leader_type', r.leader_type, ENUM.leader_type, 'E-LEAD-003', true);
    if (r.business_line !== '') {
      enumCheck(rel, r, 'business_line', r.business_line, ENUM.business_line, 'E-LEAD-005', false);
      if (org && org.org_type !== '集团') {
        err('E-LEAD-008', rel, r.__line, 'business_line', r.business_line, `business_line 只能填在集团节点上，当前节点类型为 ${org.org_type}`);
      }
    }
    if (r.sort !== '' && !/^\d{1,4}$/.test(r.sort)) {
      err('E-LEAD-007', rel, r.__line, 'sort', r.sort, 'sort 必须为 0–9999 的整数或留空');
    }
    if ([...r.remark].length > 255) {
      err('E-LEAD-010', rel, r.__line, 'remark', r.remark, `remark 长度 ${[...r.remark].length} > 255`);
    }
    if (user && user.status === '离职') {
      err('E-LEAD-006', rel, r.__line, 'user_account', r.user_account, `「${user.name}」状态为离职，不得作为组织负责人`);
    }

    const key = `${r.org_path}|${r.user_account}|${r.leader_type}|${r.business_line}`;
    if (seen.has(key)) {
      err('E-LEAD-009', rel, r.__line, 'org_path', r.org_path, '(org_path, user_account, leader_type, business_line) 完全重复');
    } else {
      seen.add(key);
    }

    if (r.leader_type === '正职') {
      const gk = `${r.org_path}|${r.business_line}`;
      if (primaryByGroup.has(gk)) {
        err(
          'E-LEAD-004',
          rel,
          r.__line,
          'leader_type',
          r.org_path,
          `组织「${r.org_path}」${r.business_line ? '业务线「' + r.business_line + '」' : ''}已有正职（第 ${primaryByGroup.get(gk).__line} 行），正职唯一、副职不限`,
        );
      } else {
        primaryByGroup.set(gk, r);
      }
    }
  }
}

// ---------------------------------------------------------------------------
// user_position.csv
// ---------------------------------------------------------------------------
function checkPosition(rel, t, orgByPath, userByAccount, userRows) {
  const seen = new Set();
  const primaryByUser = new Map();
  const covered = new Set();
  for (const r of t.rows) {
    const user = r.user_account ? userByAccount.get(r.user_account) : null;
    const org = r.org_path ? orgByPath.get(r.org_path) : null;
    if (!r.user_account) err('E-POS-002', rel, r.__line, 'user_account', '', 'user_account 为空');
    else if (!user) err('E-POS-002', rel, r.__line, 'user_account', r.user_account, 'user_account 在 user.csv 中不存在');
    if (!r.org_path) err('E-POS-001', rel, r.__line, 'org_path', '', 'org_path 为空');
    else if (!org) err('E-POS-001', rel, r.__line, 'org_path', r.org_path, 'org_path 在 org.csv 中不存在');

    if (!r.post_name) err('E-POS-006', rel, r.__line, 'post_name', '', 'post_name 为空');
    else if ([...r.post_name].length > 50) err('E-POS-006', rel, r.__line, 'post_name', r.post_name, 'post_name 超过 50 字符');
    enumCheck(rel, r, 'is_primary', r.is_primary, ENUM.is_primary, 'E-POS-003', true);
    if ([...r.remark].length > 255) {
      err('E-POS-007', rel, r.__line, 'remark', r.remark, `remark 长度 ${[...r.remark].length} > 255`);
    }

    const key = `${r.user_account}|${r.org_path}`;
    if (seen.has(key)) {
      err('E-POS-004', rel, r.__line, 'org_path', r.org_path, `(user_account, org_path) 重复，违反 sys_user_position 唯一键 uk_user_org`);
    } else {
      seen.add(key);
    }

    if (r.is_primary === '是') {
      if (primaryByUser.has(r.user_account)) {
        err('E-POS-005', rel, r.__line, 'is_primary', r.user_account, `「${r.user_account}」已有主岗（第 ${primaryByUser.get(r.user_account).__line} 行）`);
      } else {
        primaryByUser.set(r.user_account, r);
      }
    }
    if (user && r.org_path) covered.add(`${r.user_account}|${r.org_path}`);
    if (user && org && user.company_path && !(r.org_path === user.company_path || r.org_path.startsWith(user.company_path + '/'))) {
      warn(
        'W-POS-009',
        rel,
        r.__line,
        'org_path',
        r.org_path,
        `「${r.user_account}」的岗位组织不在其所属公司「${user.company_path}」子树内`,
      );
    }
  }

  for (const u of userRows) {
    if (u.dept_path && u.account && u.status === '在职' && !covered.has(`${u.account}|${u.dept_path}`)) {
      warn(
        'W-POS-008',
        'user.csv',
        u.__line,
        'dept_path',
        u.dept_path,
        `「${u.account}」的 dept_path 未出现在 user_position.csv，建议补一条主岗记录（is_primary=是）`,
      );
    }
  }
}

// ---------------------------------------------------------------------------
// user_role.csv（角色分配 → sys_user_role；role_code 校验 sys_role.code）
// ---------------------------------------------------------------------------
function checkRole(rel, t, userByAccount, orgByPath) {
  const seen = new Set();
  for (const r of t.rows) {
    const user = r.user_account ? userByAccount.get(r.user_account) : null;
    if (!r.user_account) err('E-ROLE-002', rel, r.__line, 'user_account', '', 'user_account 为空');
    else if (!user) err('E-ROLE-002', rel, r.__line, 'user_account', r.user_account, 'user_account 在 user.csv 中不存在');

    if (!r.role_code) {
      err('E-ROLE-001', rel, r.__line, 'role_code', '', 'role_code 为空');
    } else if (!ROLE_CODE_RE.test(r.role_code)) {
      err('E-ROLE-001', rel, r.__line, 'role_code', r.role_code, '角色码格式非法：应为小写蛇形 ^[a-z][a-z0-9_]{1,31}$');
    } else if (!ROLE_CODES.includes(r.role_code)) {
      err(
        'E-ROLE-001',
        rel,
        r.__line,
        'role_code',
        r.role_code,
        `角色码不在已初始化角色集内：${r.role_code}；允许值（唯一权威源 sys_role.code）${ROLE_CODES.map((c) => `${c}（${ROLE_NAMES[c] || ''}${ROLE_DATA_SCOPE[c] ? '/' + ROLE_DATA_SCOPE[c] : ''}）`).join(' / ')}`,
      );
    }

    if (r.scope_org_path !== '' && !orgByPath.has(r.scope_org_path)) {
      err('E-ROLE-004', rel, r.__line, 'scope_org_path', r.scope_org_path, 'scope_org_path 在 org.csv 中不存在');
    }
    if ([...r.remark].length > 255) {
      err('E-ROLE-005', rel, r.__line, 'remark', r.remark, `remark 长度 ${[...r.remark].length} > 255（sys_user_role.remark VARCHAR(255)）`);
    }

    // 与库约束一致：scope_org_id 为 NULL 时按 0 参与唯一键（uk_sys_user_role = user_id, role_id, scope_org_key）
    const key = `${r.user_account}|${r.role_code}|${r.scope_org_path || '\u0000NULL→0'}`;
    if (seen.has(key)) {
      err(
        'E-ROLE-003',
        rel,
        r.__line,
        'role_code',
        r.role_code,
        r.scope_org_path
          ? `同一 user + role + 数据域重复：(${r.user_account}, ${r.role_code}, ${r.scope_org_path}) 违反 uk_sys_user_role (user_id, role_id, scope_org_key)`
          : `同一 user + role + 空数据域视为同一条：(${r.user_account}, ${r.role_code}, 空) 重复，NULL 按 0 参与唯一键（uk_sys_user_role）`,
      );
    } else {
      seen.add(key);
    }
  }
}

// ---------------------------------------------------------------------------
// 主流程
// ---------------------------------------------------------------------------
function main() {
  const dirArg = process.argv[2];
  if (!dirArg) {
    console.error('用法：node tools/check-import-csv.js <导入目录>   例：node tools/check-import-csv.js oa-deploy/import');
    process.exit(2);
  }
  const dir = path.resolve(process.cwd(), dirArg);
  if (!fs.existsSync(dir) || !fs.statSync(dir).isDirectory()) {
    console.error(`目录不存在或不是目录：${dir}`);
    process.exit(2);
  }

  const tables = {};
  for (const spec of FILES) {
    tables[spec.name] = loadTable(spec, dir);
  }

  const orgByPath = checkOrg('org.csv', tables['org.csv']);
  const userByAccount = checkUser('user.csv', tables['user.csv'], orgByPath);
  checkLeader('org_leader.csv', tables['org_leader.csv'], orgByPath, userByAccount);
  checkPosition('user_position.csv', tables['user_position.csv'], orgByPath, userByAccount, tables['user.csv'].rows);
  checkRole('user_role.csv', tables['user_role.csv'], userByAccount, orgByPath);

  for (const [p, row] of orgByPath) {
    if (row.status === '停用') {
      const members = tables['user.csv'].rows.filter((u) => u.status === '在职' && (u.dept_path === p || u.dept_path.startsWith(p + '/')));
      if (members.length) {
        warn('W-ORG-017', 'org.csv', row.__line, 'status', '停用', `停用组织「${p}」下仍有 ${members.length} 名在职人员（${members.map((m) => m.account).join('、')}）`);
      }
    }
  }

  const errors = findings.filter((f) => f.severity === 'error');
  const warnings = findings.filter((f) => f.severity === 'warning');

  const byCode = new Map();
  for (const f of errors) {
    if (!byCode.has(f.code)) byCode.set(f.code, { code: f.code, count: 0, suggestion: f.suggestion });
    byCode.get(f.code).count++;
  }

  const report = {
    tool: TOOL,
    version: VERSION,
    ok: errors.length === 0,
    dir: dirArg,
    spec: {
      encoding: 'UTF-8 with BOM (EF BB BF)',
      delimiter: ',',
      headerRow: 1,
      sampleRows: `${SAMPLE_MIN}-${SAMPLE_MAX}`,
      requiredFiles: FILES.map((f) => f.name),
      roleCodeSource: process.env.IMPORT_ROLE_CODES ? 'env:IMPORT_ROLE_CODES' : 'builtin-default',
      roleCodes: ROLE_CODES,
    },
    files: FILES.map((s) => {
      const t = tables[s.name];
      return {
        file: s.name,
        title: s.title,
        table: s.table,
        exists: t.exists,
        utf8Bom: t.utf8Bom,
        encodingOk: t.encodingOk,
        headerOk: t.headerOk,
        columns: t.columns,
        header: t.header,
        expectedHeader: t.expectedHeader,
        dataRows: t.dataRows,
        emptyRows: t.emptyRows,
        orgNodes: s.name === 'org.csv' ? orgByPath.size : undefined,
        users: s.name === 'user.csv' ? userByAccount.size : undefined,
      };
    }),
    suggestions: [...byCode.values()],
    findings,
    summary: {
      filesChecked: FILES.length,
      filesFound: FILES.filter((s) => tables[s.name].exists).length,
      dataRows: FILES.reduce((n, s) => n + tables[s.name].dataRows, 0),
      errors: errors.length,
      warnings: warnings.length,
      blocking: '全量校验通过后才允许落库；任一 error 则整批回滚（见 doc/import-spec.md §5）',
    },
  };

  console.log(JSON.stringify(report, null, 2));
  process.exit(errors.length ? 1 : 0);
}

main();
