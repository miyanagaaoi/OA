#!/usr/bin/env node
/**
 * tools/gen-permission-seed.js
 * ---------------------------------------------------------------------------
 * 生成 `oa-deploy/sql/04-permissions.sql`：
 *   ① 9 个内置角色的播种（`sys_role`，先于授权，保证授权不会静默插 0 行）
 *   ② 权限树种子（**页面级菜单 + 关键动作按钮**，人工定义，不从结构基线 1:1 生成）
 *   ③ 9 个内置角色的默认授权（`sys_role_permission`，含**祖先闭包**）
 *   ④ 文件末尾的自检 SQL（角色齐备性 / 计数 / 分类 / 每角色授权数 / 孤儿 / 祖先闭包 / 越权抽查）
 *
 * 用法：
 *   node tools/gen-permission-seed.js            # 校验 + 写盘，stdout 打印 JSON 统计
 *   node tools/gen-permission-seed.js --check     # 只校验不落盘；并比对磁盘文件是否与生成结果一致
 *
 * 退出码：0 = 通过；1 = 生成器内部校验失败，或 `--check` 下磁盘文件与生成结果不一致。
 *
 * 零第三方依赖（只用 Node 内置 fs / path）。
 *
 * 为什么权限树写在代码里（而不是从 `normify-oa` 结构基线生成）：
 *   结构基线是**代码模块树**（含 `oa.design.token.color.surface.ladder` 这类内部构件），
 *   而权限树是**给业务管理员看的菜单/按钮树**。两者粒度与受众都不同，1:1 映射会把内部
 *   实现细节暴露成菜单项。因此本文件把树与授权写成可读数据结构，便于评审与再生成。
 *
 * 表结构依据：`oa-deploy/sql/01-schema.sql` §3.4 `sys_permission`、§3.5 `sys_role_permission`、§3.1 `sys_role`。
 */
'use strict';

const fs = require('fs');
const path = require('path');

const REPO_ROOT = path.resolve(__dirname, '..');
const OUT_FILE = path.join(REPO_ROOT, 'oa-deploy', 'sql', '04-permissions.sql');

// ---------------------------------------------------------------------------
// 权威值域
// ---------------------------------------------------------------------------

/**
 * 9 个内置角色（权威源：01-schema.sql §3.1 `sys_role.code` 列注释 +
 * oa-web/src/utils/authz.ts 的 `BUILT_IN_ROLE_META`）。
 * 角色名与 `oa-web/src/utils/authz.ts` 逐字一致（`dept_leader` = 「部门/科室负责人」）。
 */
const BUILT_IN_ROLES = [
  {
    code: 'admin',
    name: '系统管理员',
    roleScope: 'group',
    dataScope: 'group_all',
    remark: '内置角色，UI 禁止删除，code 与 role_scope 只读',
  },
  {
    code: 'company_admin',
    name: '分公司流程管理员',
    roleScope: 'company',
    dataScope: 'company',
    remark: '可维护本公司组织/人员/流程/表单，但不可再授权（PRD 5.2）',
  },
  {
    code: 'employee',
    name: '普通员工',
    roleScope: 'company',
    dataScope: 'self',
    remark: '门户基础权限，无审批动作；可撤回自己发起的单据',
  },
  {
    code: 'dept_leader',
    name: '部门/科室负责人',
    roleScope: 'company',
    dataScope: 'dept',
    remark: '审批人解析规则 dept_leader（直属部门负责人，本级无配置则逐级上溯）',
  },
  {
    code: 'branch_leader',
    name: '分公司分管领导',
    roleScope: 'company',
    dataScope: 'company',
    remark: '流程节点③ branch_leader',
  },
  {
    code: 'subsidiary_gm',
    name: '子公司总经理',
    roleScope: 'company',
    dataScope: 'company',
    remark: '流程节点④ subsidiary_gm',
  },
  {
    code: 'finance_owner',
    name: '集团归口（财务部）负责人',
    roleScope: 'group',
    dataScope: 'group_category',
    remark:
      '流程节点② finance_review。group_category 数据域还需在 sys_role_category 配事项类别（business/economy/admin/hr/invest），本文件不播种该表',
  },
  {
    code: 'group_leader',
    name: '集团分管领导',
    roleScope: 'group',
    dataScope: 'group_category',
    remark:
      '流程节点⑤ group_leader。group_category 数据域还需在 sys_role_category 配事项类别（business/economy/admin/hr/invest），本文件不播种该表',
  },
  {
    code: 'chairman',
    name: '集团董事长',
    roleScope: 'group',
    dataScope: 'group_all',
    remark: '流程节点⑥ chairman',
  },
];

/** `sys_role.code` 的 9 个内置角色码（白名单；顺序即播种顺序） */
const ROLE_CODES = BUILT_IN_ROLES.map((r) => r.code);

const ROLE_NAMES = BUILT_IN_ROLES.reduce((acc, r) => {
  acc[r.code] = r.name;
  return acc;
}, {});

/** `sys_role.role_scope` 值域（01-schema.sql §3.1） */
const ROLE_SCOPES = ['group', 'company'];

/** `sys_role.data_scope` 值域（01-schema.sql §3.1 的 CHECK 约束） */
const DATA_SCOPES = ['self', 'dept', 'company', 'group_all', 'group_category'];

/** `sys_permission.perm_type` 白名单（01-schema.sql §3.4） */
const PERM_TYPES = ['menu', 'button', 'api'];

/** 权限码格式：小写字母开头，冒号分段（与 DDL 注释示例 `flow:task:approve` 同风格） */
const CODE_RE = /^[a-z][a-z0-9_]*(?::[a-z0-9_]+)*$/;

const NAME_MAX = 50; // sys_permission.name VARCHAR(50)
const CODE_MAX = 64; // sys_permission.code VARCHAR(64)
const URL_MAX = 255; // sys_permission.url  VARCHAR(255)

// ---------------------------------------------------------------------------
// 节点构造器（让树定义读起来像一棵树）
// ---------------------------------------------------------------------------

/** 页面级菜单（`perm_type = menu`）；`url` 为前端路由，必须 `/` 开头 */
function menu(code, name, url, children) {
  return { code, name, type: 'menu', url, children: children || [] };
}

/** 关键动作按钮（`perm_type = button`）；按钮无独立路由，`url` 恒为 NULL */
function button(code, name) {
  return { code, name, type: 'button', url: null, children: [] };
}

// ---------------------------------------------------------------------------
// 权限树（页面级菜单 + 关键动作按钮）
// ---------------------------------------------------------------------------
/**
 * 结构口径（三条）：
 *   1. `portal:*` / `admin:*` = **页面级菜单**；`flow:*` = **关键动作按钮**。
 *   2. `sort_no` 由同级声明顺序自动生成（10, 20, 30…），保证「同一父下不重复」。
 *   3. `flow:*` 动作按钮挂在顶层分组节点 `flow`（审批动作）下，**不挂进任何 `portal:*` 菜单**：
 *      因为动作按钮是跨入口复用的（待办列表 / 详情页 / 消息跳转都能触发），一旦挂进
 *      `portal:detail`，`portal:detail:*` 的通配授权就会把「同意 / 驳回 / 终止」误发给普通员工。
 *      顶层分组节点 `url = NULL`（不产生路由，只是权限树里的分组）。
 */
const PERMISSION_TREE = [
  // ===== 端用户门户 portal:*（页面级菜单）=====================================
  menu('portal:workbench', '审批中心', '/portal/workbench', [
    menu('portal:workbench:todo', '待我审批', '/portal/workbench/todo'),
    menu('portal:workbench:done', '我已审批', '/portal/workbench/done'),
    menu('portal:workbench:mine', '我发起的', '/portal/workbench/mine'),
    menu('portal:workbench:cc', '抄送我的', '/portal/workbench/cc'),
  ]),
  menu('portal:initiate', '发起审批', '/portal/initiate', [
    menu('portal:initiate:matter', '发起事项单', '/portal/initiate/matter'),
    menu('portal:initiate:fund', '发起资金单', '/portal/initiate/fund'),
    menu('portal:initiate:contract', '发起合同单', '/portal/initiate/contract'),
    menu('portal:initiate:seal', '发起印鉴证照单', '/portal/initiate/seal'),
  ]),
  menu('portal:detail', '单据详情', '/portal/detail', [
    menu('portal:detail:thread', '审批轨迹', '/portal/detail/thread'),
    menu('portal:detail:attachment', '附件', '/portal/detail/attachment'),
    menu('portal:detail:print', '打印预览', '/portal/detail/print'),
  ]),
  menu('portal:message', '消息中心', '/portal/message'),
  menu('portal:profile', '个人中心', '/portal/profile', [
    menu('portal:profile:signature', '我的签名', '/portal/profile/signature'),
    menu('portal:profile:password', '修改口令', '/portal/profile/password'),
    menu('portal:profile:session', '我的会话设备', '/portal/profile/session'),
  ]),
  menu('portal:archive', '历史库检索（归档）', '/portal/archive', [
    menu('portal:archive:search', '归档单据检索', '/portal/archive/search'),
  ]),
  // 「扫码 / 短链」是同一入口的两种打开方式，合并为一个页面级菜单（不拆成两个菜单项）
  menu('portal:h5', 'H5 入口（扫码/短链）', '/h5'),

  // ===== 审批动作 flow:*（关键动作按钮）======================================
  menu('flow', '审批动作', null, [
    button('flow:task:approve', '同意'),
    button('flow:task:reject', '驳回'),
    button('flow:task:addsign', '加签（前/后）'),
    button('flow:task:transfer', '转办'),
    button('flow:task:reassign', '改派'),
    button('flow:task:route', '流转'),
    button('flow:task:rollback', '回退上一节点'),
    button('flow:supplement:request', '请求补件'),
    button('flow:task:withdraw', '撤回'),
    button('flow:task:terminate', '终止'),
    button('flow:print', '打印'),
    button('flow:export', '导出'),
  ]),

  // ===== 管理后台 admin:*（页面级菜单）=======================================
  menu('admin:org', '组织架构', '/admin/org', [
    menu('admin:org:tree', '组织树维护', '/admin/org/tree'),
    menu('admin:org:leader', '负责人绑定', '/admin/org/leader'),
    menu('admin:org:position', '岗位与一人多岗', '/admin/org/position'),
  ]),
  menu('admin:user', '人员管理', '/admin/user', [
    menu('admin:user:profile', '人员档案', '/admin/user/profile'),
    menu('admin:user:handover', '离职调岗交接', '/admin/user/handover'),
    menu('admin:user:import', '批量导入', '/admin/user/import'),
    // 仅系统管理员：主数据导出（前端 admin.ts / 后端 ForceReasonPolicy 按权限码判断，必须独立成项）
    menu('admin:user:export', '主数据导出', '/admin/user/export'),
  ]),
  menu('admin:role', '角色与权限', '/admin/role', [
    menu('admin:role:list', '角色维护', '/admin/role/list'),
    // 仅系统管理员：权限树勾选（「分公司管理员不可再授权」的种子层落点，必须独立成项）
    menu('admin:role:grant', '权限树勾选', '/admin/role/grant'),
    menu('admin:authz:scope', '数据域与类别', '/admin/role/scope'),
    menu('admin:authz:assign', '角色分配', '/admin/role/assign'),
  ]),
  menu('admin:flow', '流程管理', '/admin/flow', [
    menu('admin:flow:template', '流程模板', '/admin/flow/template'),
    menu('admin:flow:node', '节点配置', '/admin/flow/node'),
    menu('admin:flow:publish', '发布与停用', '/admin/flow/publish'),
  ]),
  menu('admin:form', '表单模板', '/admin/form', [
    menu('admin:form:template', '四类单据模板', '/admin/form/template'),
    menu('admin:form:field', '字段与打印标签', '/admin/form/field'),
  ]),
  menu('admin:dict', '数据字典', '/admin/dict', [
    menu('admin:dict:type', '字典类型', '/admin/dict/type'),
    menu('admin:dict:item', '字典项', '/admin/dict/item'),
    menu('admin:dict:cache', '缓存刷新', '/admin/dict/cache'),
    menu('admin:dict:io', '字典导入导出', '/admin/dict/io'),
  ]),
  menu('admin:report', '报表', '/admin/report', [
    menu('admin:report:volume', '审批量', '/admin/report/volume'),
    menu('admin:report:duration', '审批时长', '/admin/report/duration'),
    menu('admin:report:reject', '驳回率与原因', '/admin/report/reject'),
    menu('admin:report:timeout', '超时节点', '/admin/report/timeout'),
    menu('admin:report:backlog', '待办积压', '/admin/report/backlog'),
    menu('admin:report:efficiency', '审批人效率', '/admin/report/efficiency'),
    menu('admin:report:export', '报表导出', '/admin/report/export'),
  ]),
  menu('admin:audit', '审计日志', '/admin/audit', [
    menu('admin:audit:operation', '操作日志', '/admin/audit/operation'),
    menu('admin:audit:permission', '权限变更', '/admin/audit/permission'),
    menu('admin:audit:login', '登录日志', '/admin/audit/login'),
    menu('admin:audit:security', '安全日志', '/admin/audit/security'),
  ]),
  menu('admin:archive', '归档管理', '/admin/archive', [
    menu('admin:archive:policy', '归档策略', '/admin/archive/policy'),
    menu('admin:archive:job', '归档作业', '/admin/archive/job'),
    menu('admin:archive:search', '历史库检索', '/admin/archive/search'),
    menu('admin:archive:destroy', '销毁申请', '/admin/archive/destroy'),
  ]),
  menu('admin:system', '系统设置', '/admin/system', [
    menu('admin:system:session', '会话策略', '/admin/system/session'),
    menu('admin:system:password', '口令策略', '/admin/system/password'),
    menu('admin:system:retention', '保留期', '/admin/system/retention'),
    menu('admin:system:runtime', '运行期可配置项', '/admin/system/runtime'),
    menu('admin:system:key', '密钥轮换', '/admin/system/key'),
    menu('admin:system:backup', '备份与恢复演练', '/admin/system/backup'),
    menu('admin:system:slowquery', '慢查询', '/admin/system/slowquery'),
  ]),
  menu('admin:openapi', '开放接口', '/admin/openapi', [
    menu('admin:openapi:credential', '凭证管理', '/admin/openapi/credential'),
    menu('admin:openapi:readonly', '只读开放 API', '/admin/openapi/readonly'),
  ]),
  menu('admin:monitor', '监控与性能', '/admin/monitor', [
    menu('admin:monitor:capacity', '容量基线', '/admin/monitor/capacity'),
    menu('admin:monitor:latency', '延迟报告', '/admin/monitor/latency'),
    menu('admin:monitor:index', '索引与慢查询', '/admin/monitor/index'),
  ]),
];

// ---------------------------------------------------------------------------
// 9 角色默认授权
// ---------------------------------------------------------------------------
/**
 * 选择器语法：
 *   `*`           全部权限
 *   `a:b:*`       节点 `a:b` **本身 + 其全部后代**（父菜单必须一起授权，否则前端渲染不出子菜单）
 *   `a:b:c`       精确一项
 * `exclude` 在 `include` 展开后扣除，用于表达「除某一项以外」的授权。
 *
 * **祖先闭包（硬约束）**：展开后自动补上「到达每个被授予节点的全部祖先」。
 *   依据：`oa-server/…/authz/app/PermissionTreePolicy.java` 的 `requireAncestorClosed`
 *   ——「父节点未授予时子节点不得单独授予」的集合一律 400；`restore` 还会把违规项当体检结果返回。
 *   因此种子必须与界面勾选同口径：`子 ∈ G ⇒ 父 ∈ G`。
 *
 * 「分公司管理员不可再授权」（PRD 5.2）在本表落为：`company_admin` 的 include 里
 * **没有** `admin:role:*` / `admin:authz:*` / `admin:system:*`，并额外扣除 `admin:user:export`。
 */

/** 员工门户基础包（普通员工 + 6 个审批角色的公共底座） */
const EMPLOYEE_PORTAL = [
  'portal:workbench:*',
  'portal:initiate:*',
  'portal:detail:*',
  'portal:message',
  'portal:profile:*',
  'portal:archive:search',
  'portal:h5', // H5 是全员入口（PRD 6.8）
];

/** 员工可执行的动作（仅「撤回自己发起的单据」，PRD 6.4；普通员工不是审批人） */
const EMPLOYEE_ACTIONS = [
  'flow:task:withdraw',
];

/** 审批人动作包（6 个审批角色共有）；不含 改派 / 撤回 / 终止 */
const APPROVER_ACTIONS = [
  'flow',
  'flow:task:approve',
  'flow:task:reject',
  'flow:task:addsign',
  'flow:task:transfer',
  'flow:task:route',
  'flow:task:rollback',
  'flow:supplement:request',
  'flow:print',
  'flow:export',
];

/** 集团层报表查看包：报表菜单 + 6 个查看项，**不含** `admin:report:export`（报表导出仅 admin） */
const GROUP_REPORT_VIEW = ['admin:report:*'];

/** 集团层报表查看的扣除项 */
const GROUP_REPORT_VIEW_EXCLUDE = ['admin:report:export'];

/** 员工角色 = 门户基础包 + 员工动作包 */
const EMPLOYEE_BASE = [...EMPLOYEE_PORTAL, ...EMPLOYEE_ACTIONS];

/** 审批角色 = 员工角色 + 审批动作包 */
const APPROVER_BASE = [...EMPLOYEE_BASE, ...APPROVER_ACTIONS];

const ROLE_GRANTS = [
  {
    role: 'admin',
    roleName: ROLE_NAMES.admin,
    scope: '全部权限（含全部 admin:* 与 flow:*）',
    include: ['*'],
    exclude: [],
  },
  {
    role: 'company_admin',
    roleName: ROLE_NAMES.company_admin,
    scope: '门户全部 + 组织/人员/流程/表单管理；**不可再授权**',
    // 刻意不包含 admin:role:*（含 admin:role:list）—— 见 scope
    include: ['portal:*', 'admin:org:*', 'admin:user:*', 'admin:flow:*', 'admin:form:*'],
    exclude: ['admin:user:export'], // 主数据导出仅系统管理员（import-spec §9.2 T-11）
  },
  {
    role: 'employee',
    roleName: ROLE_NAMES.employee,
    scope: '门户基础（工作台/发起/详情/消息/个人中心/归档检索/H5）+ 撤回自己发起的单据；无审批动作',
    include: [...EMPLOYEE_BASE],
    exclude: [],
  },
  {
    role: 'dept_leader',
    roleName: ROLE_NAMES.dept_leader,
    scope: '员工基础包 + 审批动作包',
    include: [...APPROVER_BASE],
    exclude: [],
  },
  {
    role: 'branch_leader',
    roleName: ROLE_NAMES.branch_leader,
    scope: '员工基础包 + 审批动作包',
    include: [...APPROVER_BASE],
    exclude: [],
  },
  {
    role: 'subsidiary_gm',
    roleName: ROLE_NAMES.subsidiary_gm,
    scope: '员工基础包 + 审批动作包',
    include: [...APPROVER_BASE],
    exclude: [],
  },
  {
    role: 'finance_owner',
    roleName: ROLE_NAMES.finance_owner,
    scope: '员工基础包 + 审批动作包 + 财务归口查看项（集团层报表查看，不含报表导出）',
    // `portal:workbench:*` 已在门户基础包里，此处显式写出以对齐任务口径（展开后自动去重）
    include: [...APPROVER_BASE, 'portal:workbench:*', ...GROUP_REPORT_VIEW],
    exclude: [...GROUP_REPORT_VIEW_EXCLUDE],
  },
  {
    role: 'group_leader',
    roleName: ROLE_NAMES.group_leader,
    scope: '员工基础包 + 审批动作包 + 集团层报表查看（不含报表导出）；`portal:detail:*` 已在基础包内',
    include: [...APPROVER_BASE, 'portal:detail:*', ...GROUP_REPORT_VIEW],
    exclude: [...GROUP_REPORT_VIEW_EXCLUDE],
  },
  {
    role: 'chairman',
    roleName: ROLE_NAMES.chairman,
    scope: '员工基础包 + 审批动作包 + 集团层报表查看（不含报表导出）；`portal:detail:*` 已在基础包内',
    include: [...APPROVER_BASE, 'portal:detail:*', ...GROUP_REPORT_VIEW],
    exclude: [...GROUP_REPORT_VIEW_EXCLUDE],
  },
];

/**
 * 仅 `admin` 持有的权限码（授权表注释与校验器共用同一份口径）：
 *   · `flow:task:reassign` 改派：PRD 口径为系统管理员兜底动作；
 *   · `flow:task:terminate` 终止：管理员动作；
 *   · `admin:user:export` 主数据导出（import-spec §9.2 T-11 仅系统管理员）；
 *   · `admin:role:grant` 权限树勾选、「分公司管理员不可再授权」（PRD 5.2）；
 *   · `admin:report:export` 报表导出（与 T-11 同口径的导出治理）。
 */
const ADMIN_ONLY_CODES = [
  'flow:task:reassign',
  'flow:task:terminate',
  'admin:user:export',
  'admin:role:grant',
  'admin:report:export',
];

// ---------------------------------------------------------------------------
// 展开 / 校验
// ---------------------------------------------------------------------------

/** 深度优先摊平：保证「父行先于子行」，`sort_no` 同级自增（10,20,30…） */
function flattenTree(nodes, parentCode, depth, out) {
  for (let i = 0; i < nodes.length; i += 1) {
    const node = nodes[i];
    out.push({
      code: node.code,
      name: node.name,
      type: node.type,
      url: node.url == null ? null : node.url,
      parentCode: parentCode == null ? null : parentCode,
      depth,
      sortNo: (i + 1) * 10,
    });
    if (node.children && node.children.length) {
      flattenTree(node.children, node.code, depth + 1, out);
    }
  }
  return out;
}

function expandGrant(spec, order, byCode, errors) {
  const picked = new Set();
  for (const sel of spec.include) {
    if (sel === '*') {
      order.forEach((c) => picked.add(c));
      continue;
    }
    if (sel.endsWith(':*')) {
      const selfCode = sel.slice(0, -2);
      const prefix = sel.slice(0, -1); // 保留结尾冒号
      if (byCode.has(selfCode)) picked.add(selfCode);
      order.forEach((c) => {
        if (c.startsWith(prefix)) picked.add(c);
      });
      continue;
    }
    if (!byCode.has(sel)) {
      errors.push(`角色 ${spec.role} 引用了未定义的权限码：${sel}`);
      continue;
    }
    picked.add(sel);
  }
  for (const ex of spec.exclude) {
    if (ex.endsWith(':*')) {
      const selfCode = ex.slice(0, -2);
      const prefix = ex.slice(0, -1);
      picked.delete(selfCode);
      [...picked].forEach((c) => {
        if (c.startsWith(prefix)) picked.delete(c);
      });
      continue;
    }
    if (!byCode.has(ex)) {
      errors.push(`角色 ${spec.role} 的 exclude 引用了未定义的权限码：${ex}`);
    }
    picked.delete(ex);
  }

  // --- 祖先闭包：补齐「到达每个被授予节点的全部祖先」-----------------------------
  // 服务端 PermissionTreePolicy.requireAncestorClosed 要求 `子 ∈ G ⇒ 父 ∈ G`，否则 400。
  let changed = true;
  while (changed) {
    changed = false;
    for (const code of [...picked]) {
      const node = byCode.get(code);
      const parent = node ? node.parentCode : null;
      if (parent != null && !picked.has(parent)) {
        picked.add(parent);
        changed = true;
      }
    }
  }

  // exclude 与「后代授权」冲突时，闭包会把被排除的祖先重新引入 —— 这是口径矛盾，直接报错
  for (const ex of spec.exclude) {
    const exSelf = ex.endsWith(':*') ? ex.slice(0, -2) : ex;
    if (picked.has(exSelf)) {
      errors.push(
        `角色 ${spec.role} 的 exclude「${ex}」与 include 冲突：其被授予的后代需要该祖先，祖先闭包会把它重新引入`,
      );
    }
  }

  // 按权限树声明顺序输出，便于人工比对 SQL
  return order.filter((c) => picked.has(c));
}

/** 校验 9 个内置角色的元数据（role_scope / data_scope 值域、名称长度、备注长度） */
function validateRoles(errors, warnings) {
  const seen = new Set();
  for (const role of BUILT_IN_ROLES) {
    if (seen.has(role.code)) errors.push(`内置角色码重复：${role.code}`);
    seen.add(role.code);
    if (!ROLE_SCOPES.includes(role.roleScope)) {
      errors.push(`role_scope 非法（${role.roleScope}）：${role.code}`);
    }
    if (!DATA_SCOPES.includes(role.dataScope)) {
      errors.push(`data_scope 非法（${role.dataScope}）：${role.code}`);
    }
    if (!role.name || role.name.length > 50) errors.push(`角色名缺失或超过 50 字符：${role.code}`);
    if (role.remark && role.remark.length > 255) errors.push(`角色备注超过 255 字符：${role.code}`);
    if (role.dataScope === 'group_category') {
      warnings.push(
        `角色 ${role.code} 的数据域为 group_category：还需在 sys_role_category 配至少一个事项类别，否则该角色看不到任何数据`,
      );
    }
  }
  for (const code of ROLE_CODES) {
    if (!seen.has(code)) errors.push(`内置角色缺失：${code}`);
  }
}

function validate(rows, byCode, errors, warnings) {
  const seen = new Set();
  const parentOf = new Map();
  for (const row of rows) {
    if (seen.has(row.code)) errors.push(`权限码重复：${row.code}`);
    seen.add(row.code);

    if (!CODE_RE.test(row.code)) errors.push(`权限码格式不合法：${row.code}`);
    if (row.code.length > CODE_MAX) errors.push(`权限码超过 ${CODE_MAX} 字符：${row.code}`);
    if (!row.name || row.name.length > NAME_MAX) {
      errors.push(`权限名缺失或超过 ${NAME_MAX} 字符：${row.code}`);
    }
    if (!PERM_TYPES.includes(row.type)) errors.push(`perm_type 非法（${row.type}）：${row.code}`);
    if (row.url != null) {
      if (row.url.length > URL_MAX) errors.push(`url 超过 ${URL_MAX} 字符：${row.code}`);
      if (row.type === 'menu' && !row.url.startsWith('/')) {
        errors.push(`菜单 url 必须以 / 开头：${row.code} -> ${row.url}`);
      }
    }
    if (row.parentCode != null && !byCode.has(row.parentCode)) {
      errors.push(`父权限码不存在：${row.code} -> ${row.parentCode}`);
    }
    if (row.parentCode === row.code) errors.push(`自引用父节点：${row.code}`);
    parentOf.set(row.code, row.parentCode);
  }

  // 父先于子（摊平顺序天然满足；这里显式断言，防止后续手改数组顺序）
  const appeared = new Set();
  for (const row of rows) {
    if (row.parentCode != null && !appeared.has(row.parentCode)) {
      errors.push(`父行晚于子行出现：${row.parentCode} 未在 ${row.code} 之前插入`);
    }
    appeared.add(row.code);
  }

  // 同一父下 sort_no 不重复
  const sortKey = new Map();
  for (const row of rows) {
    const key = `${row.parentCode == null ? '<root>' : row.parentCode}|${row.sortNo}`;
    if (sortKey.has(key)) {
      errors.push(`同一父下 sort_no 重复：${sortKey.get(key)} 与 ${row.code}（sort_no=${row.sortNo}）`);
    }
    sortKey.set(key, row.code);
  }

  const total = rows.length;
  if (total < 60 || total > 90) {
    warnings.push(
      `权限项共 ${total} 项，超出任务书建议区间 60–90：任务书「至少覆盖」清单展开即需 ${total} 行` +
        `（含 ${rows.filter((r) => r.type === 'menu').length} 个页面级菜单/分组节点）。**已裁定接受 ${total} 项**，不再删减。`,
    );
  }
  const flowBad = rows.filter((r) => r.code.startsWith('flow:') && r.type !== 'button');
  if (flowBad.length) warnings.push(`flow:* 应为 button：${flowBad.map((r) => r.code).join(', ')}`);
}

function depthOf(code, parentOf, cache, guard) {
  if (cache.has(code)) return cache.get(code);
  const parent = parentOf.get(code);
  if (parent == null) {
    cache.set(code, 1);
    return 1;
  }
  if (guard <= 0) return 1;
  const d = depthOf(parent, parentOf, cache, guard - 1) + 1;
  cache.set(code, d);
  return d;
}

// ---------------------------------------------------------------------------
// SQL 渲染
// ---------------------------------------------------------------------------

function sqlStr(value) {
  if (value == null) return 'NULL';
  return `'${String(value).replace(/\\/g, '\\\\').replace(/'/g, "''")}'`;
}

/** 顶层菜单的 parent_id 为 NULL；子项的父 id 用派生表写法（绕过 MySQL「同表 INSERT … SELECT」限制） */
function parentExpr(parentCode) {
  if (parentCode == null) return 'NULL';
  return `(SELECT id FROM (SELECT id FROM sys_permission WHERE code = ${sqlStr(parentCode)}) AS t)`;
}

function renderPermissionInsert(row) {
  const values = [
    parentExpr(row.parentCode),
    sqlStr(row.type),
    sqlStr(row.code),
    sqlStr(row.name),
    sqlStr(row.url),
    String(row.sortNo),
  ].join(', ');
  return [
    `-- [深度 ${row.depth}] ${row.code}`,
    'INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES',
    `  (${values})`,
    'ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);',
    '',
  ].join('\n');
}

function chunk(list, size) {
  const out = [];
  for (let i = 0; i < list.length; i += size) out.push(list.slice(i, i + size));
  return out;
}

/** 第 1 部分：9 个内置角色播种（单条多行 VALUES，幂等） */
function renderRoleSeed() {
  const rows = BUILT_IN_ROLES.map(
    (r) =>
      `  (${sqlStr(r.code)}, ${sqlStr(r.name)}, ${sqlStr(r.roleScope)}, ${sqlStr(r.dataScope)}, ${sqlStr(r.remark)})`,
  ).join(',\n');
  return [
    '-- 角色名与 role_scope / data_scope 逐字对齐 oa-web/src/utils/authz.ts 与 01-schema.sql §3.1。',
    '-- 幂等：命中 uk_sys_role_code(code) 时只覆盖 name / role_scope / data_scope；',
    '--   **不改 code**，也不覆盖 remark（remark 允许现场按集团口径改写，重跑不被冲掉）。',
    '-- ⚠ finance_owner 与 group_leader 的 data_scope = group_category：',
    '--   它们还需在 `sys_role_category` 里配至少一个事项类别（business/economy/admin/hr/invest），',
    '--   否则数据域解析结果为空、该角色看不到任何单据。**本文件不播种 sys_role_category**',
    '--   （类别范围属 1.4 后端「数据域与类别」的运行期配置，写死会与界面配置冲突）。',
    'INSERT INTO sys_role (code, name, role_scope, data_scope, remark) VALUES',
    rows,
    'ON DUPLICATE KEY UPDATE name = VALUES(name), role_scope = VALUES(role_scope), data_scope = VALUES(data_scope);',
    '',
  ].join('\n');
}

function renderGrantInserts(role, codes) {
  const statements = [];
  const chunks = chunk(codes, 8);
  chunks.forEach((group, idx) => {
    const suffix = chunks.length > 1 ? `（第 ${idx + 1}/${chunks.length} 段）` : '';
    statements.push(
      [
        `-- ${role} ${ROLE_NAMES[role]}：共 ${codes.length} 项${suffix}`,
        'INSERT INTO sys_role_permission (role_id, permission_id, created_by)',
        'SELECT r.id, p.id, NULL',
        '  FROM sys_role r',
        `  JOIN sys_permission p ON p.code IN (${group.map(sqlStr).join(', ')})`,
        ` WHERE r.code = ${sqlStr(role)}`,
        'ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);',
        '',
      ].join('\n'),
    );
  });
  return statements.join('\n');
}

function renderHeader(rows, stats, grants) {
  const typeCount = (t) => rows.filter((r) => r.type === t).length;
  const roots = rows.filter((r) => r.parentCode == null);
  const treeLines = roots.map((root) => {
    const kids = rows.filter((r) => r.parentCode === root.code);
    const grandKids = kids.reduce(
      (acc, k) => acc + rows.filter((r) => r.parentCode === k.code).length,
      0,
    );
    return `--   · ${root.code}（${root.name}）：直接子项 ${kids.length} 个，孙项 ${grandKids} 个`;
  });

  const grantTable = grants
    .map((g) => `--   | ${g.role.padEnd(14)} | ${String(g.codes.length).padStart(3)} | ${g.scope}`)
    .join('\n');

  const roleTable = BUILT_IN_ROLES.map(
    (r) =>
      `--   | ${r.code.padEnd(14)} | ${r.name.padEnd(11)} | ${r.roleScope.padEnd(7)} | ${r.dataScope}`,
  ).join('\n');

  return `-- =============================================================================
-- 04-permissions.sql · 9 内置角色 + 权限树（页面级菜单 + 关键动作按钮）+ 角色默认授权
-- -----------------------------------------------------------------------------
-- 项目     ：集团OA审批系统（V0.4）    数据库：MySQL 8.0（InnoDB / utf8mb4）
-- 生成工具 ：tools/gen-permission-seed.js（**请勿手工编辑本文件**，改生成器后重跑）
-- 校验工具 ：node tools/check-permission-seed.js oa-deploy/sql/04-permissions.sql
-- 表结构   ：01-schema.sql §3.1 sys_role、§3.4 sys_permission、§3.5 sys_role_permission
--
-- 【用途】
--   ① 播种 sys_role 的 9 个内置角色；
--   ② 初始化权限树 sys_permission（页面级菜单 + 关键动作按钮）；
--   ③ 写入 9 个内置角色的默认授权 sys_role_permission。
--   让「角色与权限」后台一上线就有可勾选的角色与完整菜单/按钮树，而不是空表。
--
-- 【为什么不从结构基线 1:1 生成】
--   normify 结构基线是**代码模块树**（含 oa.design.token.color.surface.ladder 这类内部构件）；
--   权限树是**给业务管理员看的菜单/按钮树**，粒度与受众都不同。故按「页面级菜单 + 关键动作按钮」
--   人工定义，数据写在生成器里（便于评审与再生成）。
--
-- 【规模】
--   角色 ${stats.rolesSeeded} 个；权限项合计 ${stats.total} 项：menu ${typeCount('menu')} + button ${typeCount('button')} + api ${typeCount('api')}；
--   顶层节点 ${stats.topLevel} 个，最大深度 ${stats.maxDepth} 层；授权行 ${stats.grantRows} 行。
--   说明：任务书建议规模 60–90 项，但其「至少覆盖」清单本身展开即需 ${stats.total} 行
--   （含 ${typeCount('menu')} 个页面级菜单/分组节点）；已裁定**接受 ${stats.total} 项**，不再删减。
--
-- 【9 个内置角色】（权威源：01-schema.sql §3.1 列注释 + oa-web/src/utils/authz.ts）
--   | code           | name        | scope   | data_scope
${roleTable}
--
-- 【层级概览】（完整树见第 2 部分）
${treeLines.join('\n')}
--
-- 【幂等】
--   全部 INSERT 均为 INSERT … ON DUPLICATE KEY UPDATE：
--     · sys_role 命中 uk_sys_role_code(code)，只覆盖 name/role_scope/data_scope（**不改 code、不改 remark**）；
--     · sys_permission 命中 uk_sys_permission_code(code)，只覆盖 name/url/sort_no；
--     · sys_role_permission 命中 uk_role_permission(role_id, permission_id)，重复执行不产生重复行；
--   因此本文件可**重复执行**，且执行后：角色 ${stats.rolesSeeded} 行、权限项 ${stats.total} 行、授权 ${stats.grantRows} 行。
--   注意（已知限制）：sys_permission 的 ON DUPLICATE 分支**不重排 parent_id**。若已有环境的树形结构需要改挂
--   父节点，请先在测试库 DELETE FROM sys_permission（生产环境请走「权限树勾选」界面，并留存审计 before/after），
--   再整体重跑本文件。
--
-- 【父先于子】
--   sys_permission 的 INSERT 按树深度排序输出（父行必先于子行）；顶层节点 parent_id 为 NULL。
--   子项的 parent_id 用**派生表写法**取值，绕过 MySQL「不允许 INSERT … SELECT 同表」的限制：
--     (SELECT id FROM (SELECT id FROM sys_permission WHERE code = '<父code>') AS t)
--
-- 【执行顺序】
--   01-schema.sql  →  02-dict-seed.sql  →  03-templates.sql  →  **04-permissions.sql（本文件）**
--   本文件不建表、不动字典与模板，可单独重跑；三部分严格按 角色 → 权限树 → 授权 排列
--   （授权依赖角色与权限同时存在，且解析顺序即依赖顺序）。
--
-- 【依赖】
--   · **本文件自带 9 个内置角色的播种，故不依赖任何外部角色初始化**（第 1 部分先于授权执行）；
--   · 仅依赖 01-schema.sql 已建好 sys_role / sys_permission / sys_role_permission 三张表；
--   · finance_owner 与 group_leader 的 data_scope = group_category，还需另行配置
--     sys_role_category 的事项类别（本文件不播种，见第 1 部分注释）；
--   · 第 4 部分的「角色覆盖自检」SQL 可核对角色是否齐备（期望 0 行缺失）。
--
-- 【默认授权表】（可读定义在 tools/gen-permission-seed.js 的 ROLE_GRANTS；界面可再调）
--   | 角色码          | 项数 | 授权范围
${grantTable}
--   关键口径（越权防护的种子层保障）：
--     · admin               = 全部权限；
--     · company_admin       = portal:* + admin:org:* + admin:user:*（**除** admin:user:export）
--                             + admin:flow:* + admin:form:*；
--                             **完全不含 admin:role 整支（含只读的 admin:role:list）**、
--                             **不含 admin:authz:*、admin:system:*** —— 体现 PRD 5.2「分公司管理员不可再授权」；
--     · employee            = 门户基础包 + flow:task:withdraw（可撤回自己发起的单据，PRD 6.4）；
--                             **不含任何审批动作**（同意/驳回/加签/流转/回退/补件/终止/改派）；
--     · 6 个审批角色         = employee 基础包 + flow:* 审批动作包（不含 改派 / 终止）；
--     · finance_owner       = 审批角色包 + 财务归口查看项（admin:report:* 除 admin:report:export）；
--     · group_leader/chairman = 审批角色包 + 集团层报表查看（admin:report:* 除 admin:report:export）；
--                             portal:detail:* 已在基础包内，故与 dept_leader 的差异仅在报表。
--   仅 admin 持有的权限项（${ADMIN_ONLY_CODES.length} 项，理由逐条见下方授权段注释）：
--     ${ADMIN_ONLY_CODES.join('、')}
--     —— 改派是系统管理员兜底动作、终止是管理员动作；主数据导出（import-spec §9.2 T-11）、
--        权限树勾选（PRD 5.2 不可再授权）、报表导出（与 T-11 同口径的导出治理）同理。
--   ⚠ 授权的**祖先闭包**：本文件为每个角色补齐「到达每个被授予节点的全部祖先」，
--     因为 oa-server 的 PermissionTreePolicy.requireAncestorClosed 规定「父未授予时子不得单独授予」，
--     违规集合一律 400。种子必须与界面勾选（el-tree 联动）同口径。
--   ⚠ 权限码风格为**冒号**（DDL 注释示例 flow:task:approve 即权威风格）；
--     服务端 PermissionTreeService.CODE_PATTERN 同时接受冒号与点号，但前端判据必须与种子逐字一致。
--
-- 【末尾自检 SQL】
--   角色数 / 角色缺失探针 / 分类计数 / 每角色授权数 / 孤儿检查 / 越权抽查 —— 见文件第 4 部分。
--
-- 【本文件不含生成时间戳】
--   为了让「生成器输出 == 磁盘文件」可做漂移检测（node tools/gen-permission-seed.js --check），
--   头部刻意不写时间戳；如需溯源请查 git 提交记录。
-- =============================================================================

SET NAMES utf8mb4;

`;
}

function renderSelfCheck(rows, grants, stats) {
  const roleProbe = ROLE_CODES.map((c, i) =>
    i === 0 ? `  SELECT ${sqlStr(c)} AS code` : `  UNION ALL SELECT ${sqlStr(c)}`,
  ).join('\n');

  const roleProbeFull = BUILT_IN_ROLES.map((r, i) => {
    const lead = i === 0 ? '  SELECT' : '  UNION ALL SELECT';
    return `${lead} ${sqlStr(r.code)} AS code, ${sqlStr(r.name)} AS name, ${sqlStr(r.roleScope)} AS role_scope, ${sqlStr(r.dataScope)} AS data_scope`;
  }).join('\n');

  const grantCounts = grants
    .map((g) => `--   ${g.role.padEnd(14)} ${String(g.codes.length).padStart(3)} 行`)
    .join('\n');

  const adminOnlyProbe = ADMIN_ONLY_CODES.map(
    (c, i) =>
      `${i === 0 ? 'SELECT' : 'UNION ALL\nSELECT'} ${sqlStr(`${c} 非 admin 授权数（期望 0）`)} AS check_item, COUNT(*) AS cnt\n` +
      '  FROM sys_role_permission rp\n' +
      '  JOIN sys_role r ON r.id = rp.role_id\n' +
      '  JOIN sys_permission p ON p.id = rp.permission_id\n' +
      ` WHERE p.code = ${sqlStr(c)} AND r.code <> 'admin'`,
  ).join('\n');

  const categoryProbe = BUILT_IN_ROLES.filter((r) => r.dataScope === 'group_category')
    .map((r) => sqlStr(r.code))
    .join(', ');

  return `-- =============================================================================
-- 第 4 部分：自检 SQL（执行完本文件后逐条跑，核对期望值）
-- =============================================================================

-- ① 内置角色数：期望 ${stats.rolesSeeded}
SELECT COUNT(*) AS role_total FROM sys_role;

-- ② 角色齐备性 + 元数据逐行核对：期望 0 行（有行 = 该角色缺失或 role_scope/data_scope 与种子不一致）
SELECT w.code, w.name AS expected_name, w.role_scope AS expected_scope, w.data_scope AS expected_data_scope,
       r.id AS actual_id, r.name AS actual_name, r.role_scope AS actual_scope, r.data_scope AS actual_data_scope
  FROM (
${roleProbeFull}
  ) w
  LEFT JOIN sys_role r ON r.code = w.code
 WHERE r.id IS NULL
    OR r.name <> w.name
    OR r.role_scope <> w.role_scope
    OR r.data_scope <> w.data_scope
 ORDER BY w.code;

-- ③ 权限项总数：期望 ${rows.length}
SELECT COUNT(*) AS permission_total FROM sys_permission;

-- ④ 按类型统计：期望 menu=${rows.filter((r) => r.type === 'menu').length}，button=${rows.filter((r) => r.type === 'button').length}，api=${rows.filter((r) => r.type === 'api').length}
SELECT perm_type, COUNT(*) AS cnt FROM sys_permission GROUP BY perm_type ORDER BY perm_type;

-- ⑤ 每个角色的授权行数（期望值见下方注释；少于期望值 = 本文件的授权段未执行完）
${grantCounts}
SELECT r.code AS role_code, COUNT(*) AS granted
  FROM sys_role_permission rp
  JOIN sys_role r ON r.id = rp.role_id
 GROUP BY r.code
 ORDER BY r.code;

-- ⑥ 孤儿检查：parent_id 指向不存在的父权限；期望 0
SELECT COUNT(*) AS orphan_permissions
  FROM sys_permission c
  LEFT JOIN sys_permission p ON p.id = c.parent_id
 WHERE c.parent_id IS NOT NULL
   AND p.id IS NULL;

-- ⑦ 祖先闭包检查（PermissionTreePolicy.requireAncestorClosed 同口径）：期望 0 行
--    任何「子被授予而父未授予」的组合都会让权限树勾选保存被 400 拒绝。
SELECT r.code AS role_code, c.code AS granted_child, p.code AS missing_parent
  FROM sys_role_permission rp
  JOIN sys_role r ON r.id = rp.role_id
  JOIN sys_permission c ON c.id = rp.permission_id
  JOIN sys_permission p ON p.id = c.parent_id
 WHERE NOT EXISTS (
         SELECT 1 FROM sys_role_permission rp2
          WHERE rp2.role_id = r.id AND rp2.permission_id = p.id)
 ORDER BY r.code, c.code;

-- ⑧ 角色缺失探针（独立核对，不看元数据）：期望 0 行
SELECT w.code AS missing_role_code
  FROM (
${roleProbe}
  ) w
  LEFT JOIN sys_role r ON r.code = w.code
 WHERE r.id IS NULL
 ORDER BY w.code;

-- ⑨ 仅 admin 权限项抽查：全部期望 0
${adminOnlyProbe}
UNION ALL
SELECT 'company_admin 的 admin:system:* 授权数（期望 0）', COUNT(*)
  FROM sys_role_permission rp
  JOIN sys_role r ON r.id = rp.role_id
  JOIN sys_permission p ON p.id = rp.permission_id
 WHERE p.code LIKE 'admin:system%' AND r.code = 'company_admin'
UNION ALL
SELECT 'company_admin 的 admin:authz:* 授权数（期望 0）', COUNT(*)
  FROM sys_role_permission rp
  JOIN sys_role r ON r.id = rp.role_id
  JOIN sys_permission p ON p.id = rp.permission_id
 WHERE p.code LIKE 'admin:authz%' AND r.code = 'company_admin';

-- ⑩ 员工越权抽查：期望两行均返回 0（普通员工不得持有任何审批动作）
SELECT 'employee 的审批类 flow:* 授权数（期望 0）' AS check_item, COUNT(*) AS cnt
  FROM sys_role_permission rp
  JOIN sys_role r ON r.id = rp.role_id
  JOIN sys_permission p ON p.id = rp.permission_id
 WHERE r.code = 'employee'
   AND p.code LIKE 'flow:%'
   AND p.code <> 'flow:task:withdraw'
UNION ALL
SELECT 'employee 的 flow:task:withdraw 授权数（期望 1）', COUNT(*)
  FROM sys_role_permission rp
  JOIN sys_role r ON r.id = rp.role_id
  JOIN sys_permission p ON p.id = rp.permission_id
 WHERE r.code = 'employee' AND p.code = 'flow:task:withdraw';

-- ⑪ group_category 数据域角色的类别配置提醒：${categoryProbe} 期望各 ≥1 行
--    为空 = 该角色看不到任何单据（本文件不播种 sys_role_category，需在「数据域与类别」界面配置）
SELECT r.code AS role_code, COUNT(rc.id) AS category_count
  FROM sys_role r
  LEFT JOIN sys_role_category rc ON rc.role_id = r.id
 WHERE r.data_scope = 'group_category'
 GROUP BY r.code
 ORDER BY r.code;
`;
}

function renderSql(rows, stats, grants) {
  const parts = [renderHeader(rows, stats, grants)];

  parts.push('-- =============================================================================\n');
  parts.push('-- 第 1 部分：9 个内置角色播种（必须先于授权；幂等，命中 code 时只覆盖 name/role_scope/data_scope）\n');
  parts.push('-- =============================================================================\n\n');
  parts.push(renderRoleSeed());
  parts.push('\n');

  parts.push('-- =============================================================================\n');
  parts.push('-- 第 2 部分：权限树（父先于子，按深度排序输出）\n');
  parts.push('-- =============================================================================\n\n');

  let lastRoot = null;
  for (const row of rows) {
    const root = row.parentCode == null ? row.code : rootOf(row, rows);
    if (root !== lastRoot) {
      parts.push(`-- ------------------------------ 顶层节点：${root} ------------------------------\n\n`);
      lastRoot = root;
    }
    parts.push(renderPermissionInsert(row));
    parts.push('\n');
  }

  parts.push('-- =============================================================================\n');
  parts.push('-- 第 3 部分：9 角色默认授权（幂等；依赖第 1 部分的角色与第 2 部分的权限码）\n');
  parts.push('-- =============================================================================\n\n');
  for (const g of grants) {
    parts.push(`-- ===== 角色 ${g.role}（${g.roleName}）：${g.codes.length} 项 =====\n`);
    parts.push(`-- 范围：${g.scope}\n`);
    const adminOnlyHeld = ADMIN_ONLY_CODES.filter((c) => g.codes.includes(c));
    if (adminOnlyHeld.length) {
      parts.push(`-- 仅 admin 权限项（本角色持有 ${adminOnlyHeld.length} 项）：${adminOnlyHeld.join('、')}\n`);
    }
    parts.push(renderGrantInserts(g.role, g.codes));
    parts.push('\n');
  }

  parts.push(renderSelfCheck(rows, grants, stats));
  return parts.join('');
}

function rootOf(row, rows) {
  let cur = row;
  const guard = 64;
  let i = 0;
  while (cur.parentCode != null && i < guard) {
    const parent = rows.find((r) => r.code === cur.parentCode);
    if (!parent) return '<unknown>';
    cur = parent;
    i += 1;
  }
  return cur.code;
}

// ---------------------------------------------------------------------------
// main
// ---------------------------------------------------------------------------

function main() {
  const argv = process.argv.slice(2);
  const checkOnly = argv.includes('--check');

  const errors = [];
  const warnings = [];

  const rows = flattenTree(PERMISSION_TREE, null, 1, []);
  const byCode = new Map(rows.map((r) => [r.code, r]));
  const order = rows.map((r) => r.code);

  validate(rows, byCode, errors, warnings);
  validateRoles(errors, warnings);

  const grants = ROLE_GRANTS.map((spec) => ({
    role: spec.role,
    roleName: spec.roleName,
    scope: spec.scope,
    codes: expandGrant(spec, order, byCode, errors),
  }));
  const grantOf = (role) => grants.find((g) => g.role === role);

  // 角色码白名单 + 9 个角色都必须有授权段
  for (const g of grants) {
    if (!ROLE_CODES.includes(g.role)) errors.push(`角色码不在 9 码白名单内：${g.role}`);
  }
  for (const code of ROLE_CODES) {
    if (!grantOf(code)) errors.push(`内置角色缺少默认授权段：${code}`);
  }

  // admin 必须拿到全部权限
  const missingForAdmin = order.filter((c) => !grantOf('admin').codes.includes(c));
  if (missingForAdmin.length) {
    errors.push(`admin 未覆盖全部权限：缺 ${missingForAdmin.join(', ')}`);
  }

  // company_admin 越权防护
  const forbidden = grantOf('company_admin').codes.filter(
    (c) =>
      c === 'admin:user:export' ||
      c === 'admin:role:grant' ||
      c.startsWith('admin:authz:') ||
      c.startsWith('admin:system:'),
  );
  if (forbidden.length) {
    errors.push(`company_admin 越权：不应包含 ${forbidden.join(', ')}`);
  }

  // company_admin 不得持有 admin:role 整支（含只读的 admin:role:list）—— 裁定 3「不可再授权」
  const caRoleBranch = grantOf('company_admin').codes.filter(
    (c) => c === 'admin:role' || c.startsWith('admin:role:'),
  );
  if (caRoleBranch.length) {
    errors.push(`company_admin 不应持有 admin:role 整支：${caRoleBranch.join(', ')}`);
  }

  // employee 必须持有 撤回 + H5，且不得持有任何审批动作
  for (const code of ['flow:task:withdraw', 'portal:h5']) {
    if (!grantOf('employee').codes.includes(code)) {
      errors.push(`employee 缺少必要权限项：${code}`);
    }
  }
  const employeeApproverActions = grantOf('employee').codes.filter(
    (c) => c.startsWith('flow:') && c !== 'flow:task:withdraw' && c !== 'flow',
  );
  if (employeeApproverActions.length) {
    errors.push(`employee 不应持有审批动作：${employeeApproverActions.join(', ')}`);
  }

  // group_leader / chairman 必须持有集团层报表查看，且不含报表导出
  for (const role of ['group_leader', 'chairman']) {
    const codes = grantOf(role).codes;
    if (!codes.includes('admin:report')) errors.push(`${role} 缺少报表菜单节点：admin:report`);
    const reportView = codes.filter((c) => c.startsWith('admin:report:') && c !== 'admin:report:export');
    if (reportView.length === 0) errors.push(`${role} 缺少集团层报表查看项（admin:report:*）`);
    if (codes.includes('admin:report:export')) errors.push(`${role} 不应持有 admin:report:export`);
  }

  // 仅 admin 权限项：除 admin 外任何角色都不得持有
  for (const code of ADMIN_ONLY_CODES) {
    if (!byCode.has(code)) errors.push(`ADMIN_ONLY_CODES 引用了未定义的权限码：${code}`);
    for (const g of grants) {
      if (g.role !== 'admin' && g.codes.includes(code)) {
        errors.push(`仅 admin 权限项被下放：${g.role} -> ${code}`);
      }
    }
  }

  const depthCache = new Map();
  const parentOf = new Map(rows.map((r) => [r.code, r.parentCode]));
  const depthDistribution = {};
  rows.forEach((r) => {
    const d = depthOf(r.code, parentOf, depthCache, 64);
    depthDistribution[d] = (depthDistribution[d] || 0) + 1;
  });
  const maxDepth = Math.max(...Object.keys(depthDistribution).map(Number));

  const stats = {
    rolesSeeded: BUILT_IN_ROLES.length,
    total: rows.length,
    menus: rows.filter((r) => r.type === 'menu').length,
    buttons: rows.filter((r) => r.type === 'button').length,
    apis: rows.filter((r) => r.type === 'api').length,
    topLevel: rows.filter((r) => r.parentCode == null).length,
    maxDepth,
    depthDistribution,
    grantRows: grants.reduce((acc, g) => acc + g.codes.length, 0),
    roles: grants.length,
    grantedPerRole: grants.reduce((acc, g) => {
      acc[g.role] = g.codes.length;
      return acc;
    }, {}),
    adminOnlyCodes: ADMIN_ONLY_CODES,
  };

  if (errors.length) {
    process.stdout.write(
      `${JSON.stringify({ ok: false, file: path.relative(REPO_ROOT, OUT_FILE), stats, errors, warnings }, null, 2)}\n`,
    );
    return 1;
  }

  const sql = renderSql(rows, stats, grants);

  if (checkOnly) {
    let upToDate = false;
    let reason = '';
    if (!fs.existsSync(OUT_FILE)) {
      reason = '目标文件不存在，请先运行 node tools/gen-permission-seed.js';
    } else {
      const onDisk = normalize(fs.readFileSync(OUT_FILE, 'utf8'));
      upToDate = onDisk === normalize(sql);
      reason = upToDate ? '' : '磁盘文件与生成结果不一致（生成器已变更或文件被手工编辑）';
    }
    process.stdout.write(
      `${JSON.stringify(
        { ok: upToDate, check: true, file: path.relative(REPO_ROOT, OUT_FILE), upToDate, reason, stats, errors, warnings },
        null,
        2,
      )}\n`,
    );
    return upToDate ? 0 : 1;
  }

  fs.mkdirSync(path.dirname(OUT_FILE), { recursive: true });
  fs.writeFileSync(OUT_FILE, sql, 'utf8');

  process.stdout.write(
    `${JSON.stringify(
      {
        ok: true,
        file: path.relative(REPO_ROOT, OUT_FILE).split(path.sep).join('/'),
        bytes: Buffer.byteLength(sql, 'utf8'),
        lines: sql.split('\n').length,
        stats,
        errors,
        warnings,
      },
      null,
      2,
    )}\n`,
  );
  return 0;
}

function normalize(text) {
  return text.replace(/^\uFEFF/, '').replace(/\r\n/g, '\n');
}

process.exitCode = main();
