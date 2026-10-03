-- ============================================================================
-- V4 内置角色 + 权限树 + 角色授权（9 角色 / 94 权限项 / 376 授权行，幂等）
-- ----------------------------------------------------------------------------
-- 生成器: tools/build-flyway-migrations.js sha256=1a70ffc3f41d
-- 确定性: 无墙钟时间戳/随机量；同一输入重复生成逐字节一致（Flyway checksum 稳定）。
-- 请勿手工编辑本文件：改 oa-deploy/sql 或文档后重跑生成器。
-- 来源: oa-deploy/sql/04-permissions.sql ← tools/gen-permission-seed.js（数据在此定义） sha256=841d1ef1c339
-- 三段顺序不可调换：① 播种 sys_role（9 个内置角色）→ ② 播种 sys_permission（权限树，父先于子）→ ③ 播种 sys_role_permission。
-- 若角色段被移到授权段之后，授权 JOIN 不到角色会**静默插入 0 行**（表现为登录后没有菜单）——check-permission-seed.js 有顺序断言。
-- 权限码为**冒号风格**（如 admin:user:export），与 oa-web 的前端判据逐字一致。
-- finance_owner / group_leader 的 data_scope=group_category，还需在 sys_role_category 配事项类别五值（本迁移不播种该表）。
-- ============================================================================

-- =============================================================================
-- 第 1 部分：9 个内置角色播种（必须先于授权；幂等，命中 code 时只覆盖 name/role_scope/data_scope）
-- =============================================================================

-- 角色名与 role_scope / data_scope 逐字对齐 oa-web/src/utils/authz.ts 与 01-schema.sql §3.1。
-- 幂等：命中 uk_sys_role_code(code) 时只覆盖 name / role_scope / data_scope；
--   **不改 code**，也不覆盖 remark（remark 允许现场按集团口径改写，重跑不被冲掉）。
-- ⚠ finance_owner 与 group_leader 的 data_scope = group_category：
--   它们还需在 `sys_role_category` 里配至少一个事项类别（business/economy/admin/hr/invest），
--   否则数据域解析结果为空、该角色看不到任何单据。**本文件不播种 sys_role_category**
--   （类别范围属 1.4 后端「数据域与类别」的运行期配置，写死会与界面配置冲突）。
INSERT INTO sys_role (code, name, role_scope, data_scope, remark) VALUES
  ('admin', '系统管理员', 'group', 'group_all', '内置角色，UI 禁止删除，code 与 role_scope 只读'),
  ('company_admin', '分公司流程管理员', 'company', 'company', '可维护本公司组织/人员/流程/表单，但不可再授权（PRD 5.2）'),
  ('employee', '普通员工', 'company', 'self', '门户基础权限，无审批动作；可撤回自己发起的单据'),
  ('dept_leader', '部门/科室负责人', 'company', 'dept', '审批人解析规则 dept_leader（直属部门负责人，本级无配置则逐级上溯）'),
  ('branch_leader', '分公司分管领导', 'company', 'company', '流程节点③ branch_leader'),
  ('subsidiary_gm', '子公司总经理', 'company', 'company', '流程节点④ subsidiary_gm'),
  ('finance_owner', '集团归口（财务部）负责人', 'group', 'group_category', '流程节点② finance_review。group_category 数据域还需在 sys_role_category 配事项类别（business/economy/admin/hr/invest），本文件不播种该表'),
  ('group_leader', '集团分管领导', 'group', 'group_category', '流程节点⑤ group_leader。group_category 数据域还需在 sys_role_category 配事项类别（business/economy/admin/hr/invest），本文件不播种该表'),
  ('chairman', '集团董事长', 'group', 'group_all', '流程节点⑥ chairman')
ON DUPLICATE KEY UPDATE name = VALUES(name), role_scope = VALUES(role_scope), data_scope = VALUES(data_scope);

-- =============================================================================
-- 第 2 部分：权限树（父先于子，按深度排序输出）
-- =============================================================================

-- ------------------------------ 顶层节点：portal:workbench ------------------------------

-- [深度 1] portal:workbench
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  (NULL, 'menu', 'portal:workbench', '审批中心', '/portal/workbench', 10)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] portal:workbench:todo
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'portal:workbench') AS t), 'menu', 'portal:workbench:todo', '待我审批', '/portal/workbench/todo', 10)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] portal:workbench:done
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'portal:workbench') AS t), 'menu', 'portal:workbench:done', '我已审批', '/portal/workbench/done', 20)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] portal:workbench:mine
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'portal:workbench') AS t), 'menu', 'portal:workbench:mine', '我发起的', '/portal/workbench/mine', 30)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] portal:workbench:cc
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'portal:workbench') AS t), 'menu', 'portal:workbench:cc', '抄送我的', '/portal/workbench/cc', 40)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- ------------------------------ 顶层节点：portal:initiate ------------------------------

-- [深度 1] portal:initiate
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  (NULL, 'menu', 'portal:initiate', '发起审批', '/portal/initiate', 20)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] portal:initiate:matter
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'portal:initiate') AS t), 'menu', 'portal:initiate:matter', '发起事项单', '/portal/initiate/matter', 10)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] portal:initiate:fund
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'portal:initiate') AS t), 'menu', 'portal:initiate:fund', '发起资金单', '/portal/initiate/fund', 20)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] portal:initiate:contract
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'portal:initiate') AS t), 'menu', 'portal:initiate:contract', '发起合同单', '/portal/initiate/contract', 30)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] portal:initiate:seal
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'portal:initiate') AS t), 'menu', 'portal:initiate:seal', '发起印鉴证照单', '/portal/initiate/seal', 40)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- ------------------------------ 顶层节点：portal:detail ------------------------------

-- [深度 1] portal:detail
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  (NULL, 'menu', 'portal:detail', '单据详情', '/portal/detail', 30)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] portal:detail:thread
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'portal:detail') AS t), 'menu', 'portal:detail:thread', '审批轨迹', '/portal/detail/thread', 10)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] portal:detail:attachment
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'portal:detail') AS t), 'menu', 'portal:detail:attachment', '附件', '/portal/detail/attachment', 20)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] portal:detail:print
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'portal:detail') AS t), 'menu', 'portal:detail:print', '打印预览', '/portal/detail/print', 30)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- ------------------------------ 顶层节点：portal:message ------------------------------

-- [深度 1] portal:message
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  (NULL, 'menu', 'portal:message', '消息中心', '/portal/message', 40)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- ------------------------------ 顶层节点：portal:profile ------------------------------

-- [深度 1] portal:profile
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  (NULL, 'menu', 'portal:profile', '个人中心', '/portal/profile', 50)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] portal:profile:signature
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'portal:profile') AS t), 'menu', 'portal:profile:signature', '我的签名', '/portal/profile/signature', 10)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] portal:profile:password
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'portal:profile') AS t), 'menu', 'portal:profile:password', '修改口令', '/portal/profile/password', 20)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] portal:profile:session
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'portal:profile') AS t), 'menu', 'portal:profile:session', '我的会话设备', '/portal/profile/session', 30)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- ------------------------------ 顶层节点：portal:archive ------------------------------

-- [深度 1] portal:archive
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  (NULL, 'menu', 'portal:archive', '历史库检索（归档）', '/portal/archive', 60)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] portal:archive:search
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'portal:archive') AS t), 'menu', 'portal:archive:search', '归档单据检索', '/portal/archive/search', 10)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- ------------------------------ 顶层节点：portal:h5 ------------------------------

-- [深度 1] portal:h5
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  (NULL, 'menu', 'portal:h5', 'H5 入口（扫码/短链）', '/h5', 70)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- ------------------------------ 顶层节点：flow ------------------------------

-- [深度 1] flow
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  (NULL, 'menu', 'flow', '审批动作', NULL, 80)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] flow:task:approve
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'flow') AS t), 'button', 'flow:task:approve', '同意', NULL, 10)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] flow:task:reject
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'flow') AS t), 'button', 'flow:task:reject', '驳回', NULL, 20)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] flow:task:addsign
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'flow') AS t), 'button', 'flow:task:addsign', '加签（前/后）', NULL, 30)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] flow:task:transfer
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'flow') AS t), 'button', 'flow:task:transfer', '转办', NULL, 40)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] flow:task:reassign
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'flow') AS t), 'button', 'flow:task:reassign', '改派', NULL, 50)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] flow:task:route
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'flow') AS t), 'button', 'flow:task:route', '流转', NULL, 60)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] flow:task:rollback
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'flow') AS t), 'button', 'flow:task:rollback', '回退上一节点', NULL, 70)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] flow:supplement:request
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'flow') AS t), 'button', 'flow:supplement:request', '请求补件', NULL, 80)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] flow:task:withdraw
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'flow') AS t), 'button', 'flow:task:withdraw', '撤回', NULL, 90)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] flow:task:terminate
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'flow') AS t), 'button', 'flow:task:terminate', '终止', NULL, 100)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] flow:print
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'flow') AS t), 'button', 'flow:print', '打印', NULL, 110)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] flow:export
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'flow') AS t), 'button', 'flow:export', '导出', NULL, 120)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- ------------------------------ 顶层节点：admin:org ------------------------------

-- [深度 1] admin:org
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  (NULL, 'menu', 'admin:org', '组织架构', '/admin/org', 90)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:org:tree
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:org') AS t), 'menu', 'admin:org:tree', '组织树维护', '/admin/org/tree', 10)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:org:leader
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:org') AS t), 'menu', 'admin:org:leader', '负责人绑定', '/admin/org/leader', 20)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:org:position
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:org') AS t), 'menu', 'admin:org:position', '岗位与一人多岗', '/admin/org/position', 30)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- ------------------------------ 顶层节点：admin:user ------------------------------

-- [深度 1] admin:user
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  (NULL, 'menu', 'admin:user', '人员管理', '/admin/user', 100)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:user:profile
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:user') AS t), 'menu', 'admin:user:profile', '人员档案', '/admin/user/profile', 10)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:user:handover
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:user') AS t), 'menu', 'admin:user:handover', '离职调岗交接', '/admin/user/handover', 20)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:user:import
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:user') AS t), 'menu', 'admin:user:import', '批量导入', '/admin/user/import', 30)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:user:export
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:user') AS t), 'menu', 'admin:user:export', '主数据导出', '/admin/user/export', 40)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- ------------------------------ 顶层节点：admin:role ------------------------------

-- [深度 1] admin:role
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  (NULL, 'menu', 'admin:role', '角色与权限', '/admin/role', 110)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:role:list
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:role') AS t), 'menu', 'admin:role:list', '角色维护', '/admin/role/list', 10)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:role:grant
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:role') AS t), 'menu', 'admin:role:grant', '权限树勾选', '/admin/role/grant', 20)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:authz:scope
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:role') AS t), 'menu', 'admin:authz:scope', '数据域与类别', '/admin/role/scope', 30)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:authz:assign
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:role') AS t), 'menu', 'admin:authz:assign', '角色分配', '/admin/role/assign', 40)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- ------------------------------ 顶层节点：admin:flow ------------------------------

-- [深度 1] admin:flow
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  (NULL, 'menu', 'admin:flow', '流程管理', '/admin/flow', 120)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:flow:template
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:flow') AS t), 'menu', 'admin:flow:template', '流程模板', '/admin/flow/template', 10)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:flow:node
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:flow') AS t), 'menu', 'admin:flow:node', '节点配置', '/admin/flow/node', 20)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:flow:publish
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:flow') AS t), 'menu', 'admin:flow:publish', '发布与停用', '/admin/flow/publish', 30)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- ------------------------------ 顶层节点：admin:form ------------------------------

-- [深度 1] admin:form
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  (NULL, 'menu', 'admin:form', '表单模板', '/admin/form', 130)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:form:template
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:form') AS t), 'menu', 'admin:form:template', '四类单据模板', '/admin/form/template', 10)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:form:field
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:form') AS t), 'menu', 'admin:form:field', '字段与打印标签', '/admin/form/field', 20)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- ------------------------------ 顶层节点：admin:dict ------------------------------

-- [深度 1] admin:dict
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  (NULL, 'menu', 'admin:dict', '数据字典', '/admin/dict', 140)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:dict:type
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:dict') AS t), 'menu', 'admin:dict:type', '字典类型', '/admin/dict/type', 10)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:dict:item
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:dict') AS t), 'menu', 'admin:dict:item', '字典项', '/admin/dict/item', 20)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:dict:cache
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:dict') AS t), 'menu', 'admin:dict:cache', '缓存刷新', '/admin/dict/cache', 30)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:dict:io
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:dict') AS t), 'menu', 'admin:dict:io', '字典导入导出', '/admin/dict/io', 40)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- ------------------------------ 顶层节点：admin:report ------------------------------

-- [深度 1] admin:report
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  (NULL, 'menu', 'admin:report', '报表', '/admin/report', 150)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:report:volume
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:report') AS t), 'menu', 'admin:report:volume', '审批量', '/admin/report/volume', 10)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:report:duration
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:report') AS t), 'menu', 'admin:report:duration', '审批时长', '/admin/report/duration', 20)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:report:reject
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:report') AS t), 'menu', 'admin:report:reject', '驳回率与原因', '/admin/report/reject', 30)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:report:timeout
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:report') AS t), 'menu', 'admin:report:timeout', '超时节点', '/admin/report/timeout', 40)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:report:backlog
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:report') AS t), 'menu', 'admin:report:backlog', '待办积压', '/admin/report/backlog', 50)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:report:efficiency
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:report') AS t), 'menu', 'admin:report:efficiency', '审批人效率', '/admin/report/efficiency', 60)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:report:export
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:report') AS t), 'menu', 'admin:report:export', '报表导出', '/admin/report/export', 70)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- ------------------------------ 顶层节点：admin:audit ------------------------------

-- [深度 1] admin:audit
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  (NULL, 'menu', 'admin:audit', '审计日志', '/admin/audit', 160)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:audit:operation
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:audit') AS t), 'menu', 'admin:audit:operation', '操作日志', '/admin/audit/operation', 10)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:audit:permission
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:audit') AS t), 'menu', 'admin:audit:permission', '权限变更', '/admin/audit/permission', 20)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:audit:login
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:audit') AS t), 'menu', 'admin:audit:login', '登录日志', '/admin/audit/login', 30)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:audit:security
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:audit') AS t), 'menu', 'admin:audit:security', '安全日志', '/admin/audit/security', 40)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- ------------------------------ 顶层节点：admin:archive ------------------------------

-- [深度 1] admin:archive
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  (NULL, 'menu', 'admin:archive', '归档管理', '/admin/archive', 170)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:archive:policy
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:archive') AS t), 'menu', 'admin:archive:policy', '归档策略', '/admin/archive/policy', 10)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:archive:job
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:archive') AS t), 'menu', 'admin:archive:job', '归档作业', '/admin/archive/job', 20)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:archive:search
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:archive') AS t), 'menu', 'admin:archive:search', '历史库检索', '/admin/archive/search', 30)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:archive:destroy
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:archive') AS t), 'menu', 'admin:archive:destroy', '销毁申请', '/admin/archive/destroy', 40)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- ------------------------------ 顶层节点：admin:system ------------------------------

-- [深度 1] admin:system
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  (NULL, 'menu', 'admin:system', '系统设置', '/admin/system', 180)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:system:session
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:system') AS t), 'menu', 'admin:system:session', '会话策略', '/admin/system/session', 10)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:system:password
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:system') AS t), 'menu', 'admin:system:password', '口令策略', '/admin/system/password', 20)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:system:retention
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:system') AS t), 'menu', 'admin:system:retention', '保留期', '/admin/system/retention', 30)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:system:runtime
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:system') AS t), 'menu', 'admin:system:runtime', '运行期可配置项', '/admin/system/runtime', 40)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:system:key
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:system') AS t), 'menu', 'admin:system:key', '密钥轮换', '/admin/system/key', 50)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:system:backup
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:system') AS t), 'menu', 'admin:system:backup', '备份与恢复演练', '/admin/system/backup', 60)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:system:slowquery
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:system') AS t), 'menu', 'admin:system:slowquery', '慢查询', '/admin/system/slowquery', 70)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- ------------------------------ 顶层节点：admin:openapi ------------------------------

-- [深度 1] admin:openapi
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  (NULL, 'menu', 'admin:openapi', '开放接口', '/admin/openapi', 190)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:openapi:credential
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:openapi') AS t), 'menu', 'admin:openapi:credential', '凭证管理', '/admin/openapi/credential', 10)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:openapi:readonly
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:openapi') AS t), 'menu', 'admin:openapi:readonly', '只读开放 API', '/admin/openapi/readonly', 20)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- ------------------------------ 顶层节点：admin:monitor ------------------------------

-- [深度 1] admin:monitor
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  (NULL, 'menu', 'admin:monitor', '监控与性能', '/admin/monitor', 200)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:monitor:capacity
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:monitor') AS t), 'menu', 'admin:monitor:capacity', '容量基线', '/admin/monitor/capacity', 10)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:monitor:latency
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:monitor') AS t), 'menu', 'admin:monitor:latency', '延迟报告', '/admin/monitor/latency', 20)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- [深度 2] admin:monitor:index
INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no) VALUES
  ((SELECT id FROM (SELECT id FROM sys_permission WHERE code = 'admin:monitor') AS t), 'menu', 'admin:monitor:index', '索引与慢查询', '/admin/monitor/index', 30)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), sort_no = VALUES(sort_no);

-- =============================================================================
-- 第 3 部分：9 角色默认授权（幂等；依赖第 1 部分的角色与第 2 部分的权限码）
-- =============================================================================

-- ===== 角色 admin（系统管理员）：94 项 =====
-- 范围：全部权限（含全部 admin:* 与 flow:*）
-- 仅 admin 权限项（本角色持有 4 项）：flow:task:reassign、admin:user:export、admin:role:grant、admin:report:export
-- admin 系统管理员：共 94 项（第 1/12 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:workbench', 'portal:workbench:todo', 'portal:workbench:done', 'portal:workbench:mine', 'portal:workbench:cc', 'portal:initiate', 'portal:initiate:matter', 'portal:initiate:fund')
 WHERE r.code = 'admin'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- admin 系统管理员：共 94 项（第 2/12 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:initiate:contract', 'portal:initiate:seal', 'portal:detail', 'portal:detail:thread', 'portal:detail:attachment', 'portal:detail:print', 'portal:message', 'portal:profile')
 WHERE r.code = 'admin'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- admin 系统管理员：共 94 项（第 3/12 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:profile:signature', 'portal:profile:password', 'portal:profile:session', 'portal:archive', 'portal:archive:search', 'portal:h5', 'flow', 'flow:task:approve')
 WHERE r.code = 'admin'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- admin 系统管理员：共 94 项（第 4/12 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('flow:task:reject', 'flow:task:addsign', 'flow:task:transfer', 'flow:task:reassign', 'flow:task:route', 'flow:task:rollback', 'flow:supplement:request', 'flow:task:withdraw')
 WHERE r.code = 'admin'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- admin 系统管理员：共 94 项（第 5/12 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('flow:task:terminate', 'flow:print', 'flow:export', 'admin:org', 'admin:org:tree', 'admin:org:leader', 'admin:org:position', 'admin:user')
 WHERE r.code = 'admin'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- admin 系统管理员：共 94 项（第 6/12 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('admin:user:profile', 'admin:user:handover', 'admin:user:import', 'admin:user:export', 'admin:role', 'admin:role:list', 'admin:role:grant', 'admin:authz:scope')
 WHERE r.code = 'admin'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- admin 系统管理员：共 94 项（第 7/12 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('admin:authz:assign', 'admin:flow', 'admin:flow:template', 'admin:flow:node', 'admin:flow:publish', 'admin:form', 'admin:form:template', 'admin:form:field')
 WHERE r.code = 'admin'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- admin 系统管理员：共 94 项（第 8/12 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('admin:dict', 'admin:dict:type', 'admin:dict:item', 'admin:dict:cache', 'admin:dict:io', 'admin:report', 'admin:report:volume', 'admin:report:duration')
 WHERE r.code = 'admin'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- admin 系统管理员：共 94 项（第 9/12 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('admin:report:reject', 'admin:report:timeout', 'admin:report:backlog', 'admin:report:efficiency', 'admin:report:export', 'admin:audit', 'admin:audit:operation', 'admin:audit:permission')
 WHERE r.code = 'admin'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- admin 系统管理员：共 94 项（第 10/12 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('admin:audit:login', 'admin:audit:security', 'admin:archive', 'admin:archive:policy', 'admin:archive:job', 'admin:archive:search', 'admin:archive:destroy', 'admin:system')
 WHERE r.code = 'admin'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- admin 系统管理员：共 94 项（第 11/12 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('admin:system:session', 'admin:system:password', 'admin:system:retention', 'admin:system:runtime', 'admin:system:key', 'admin:system:backup', 'admin:system:slowquery', 'admin:openapi')
 WHERE r.code = 'admin'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- admin 系统管理员：共 94 项（第 12/12 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('admin:openapi:credential', 'admin:openapi:readonly', 'admin:monitor', 'admin:monitor:capacity', 'admin:monitor:latency', 'admin:monitor:index')
 WHERE r.code = 'admin'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- ===== 角色 company_admin（分公司流程管理员）：38 项 =====
-- 范围：**发起审批（门户基础权限 flow）** + 门户全部 + 组织/人员/流程/表单管理；**不可再授权**
-- company_admin 分公司流程管理员：共 38 项（第 1/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:workbench', 'portal:workbench:todo', 'portal:workbench:done', 'portal:workbench:mine', 'portal:workbench:cc', 'portal:initiate', 'portal:initiate:matter', 'portal:initiate:fund')
 WHERE r.code = 'company_admin'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- company_admin 分公司流程管理员：共 38 项（第 2/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:initiate:contract', 'portal:initiate:seal', 'portal:detail', 'portal:detail:thread', 'portal:detail:attachment', 'portal:detail:print', 'portal:message', 'portal:profile')
 WHERE r.code = 'company_admin'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- company_admin 分公司流程管理员：共 38 项（第 3/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:profile:signature', 'portal:profile:password', 'portal:profile:session', 'portal:archive', 'portal:archive:search', 'portal:h5', 'flow', 'admin:org')
 WHERE r.code = 'company_admin'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- company_admin 分公司流程管理员：共 38 项（第 4/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('admin:org:tree', 'admin:org:leader', 'admin:org:position', 'admin:user', 'admin:user:profile', 'admin:user:handover', 'admin:user:import', 'admin:flow')
 WHERE r.code = 'company_admin'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- company_admin 分公司流程管理员：共 38 项（第 5/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('admin:flow:template', 'admin:flow:node', 'admin:flow:publish', 'admin:form', 'admin:form:template', 'admin:form:field')
 WHERE r.code = 'company_admin'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- ===== 角色 employee（普通员工）：24 项 =====
-- 范围：门户基础（工作台/发起/详情/消息/个人中心/归档检索/H5）+ 撤回自己发起的单据；无审批动作
-- employee 普通员工：共 24 项（第 1/3 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:workbench', 'portal:workbench:todo', 'portal:workbench:done', 'portal:workbench:mine', 'portal:workbench:cc', 'portal:initiate', 'portal:initiate:matter', 'portal:initiate:fund')
 WHERE r.code = 'employee'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- employee 普通员工：共 24 项（第 2/3 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:initiate:contract', 'portal:initiate:seal', 'portal:detail', 'portal:detail:thread', 'portal:detail:attachment', 'portal:detail:print', 'portal:message', 'portal:profile')
 WHERE r.code = 'employee'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- employee 普通员工：共 24 项（第 3/3 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:profile:signature', 'portal:profile:password', 'portal:profile:session', 'portal:archive', 'portal:archive:search', 'portal:h5', 'flow', 'flow:task:withdraw')
 WHERE r.code = 'employee'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- ===== 角色 dept_leader（部门/科室负责人）：33 项 =====
-- 范围：员工基础包 + 审批动作包
-- dept_leader 部门/科室负责人：共 33 项（第 1/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:workbench', 'portal:workbench:todo', 'portal:workbench:done', 'portal:workbench:mine', 'portal:workbench:cc', 'portal:initiate', 'portal:initiate:matter', 'portal:initiate:fund')
 WHERE r.code = 'dept_leader'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- dept_leader 部门/科室负责人：共 33 项（第 2/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:initiate:contract', 'portal:initiate:seal', 'portal:detail', 'portal:detail:thread', 'portal:detail:attachment', 'portal:detail:print', 'portal:message', 'portal:profile')
 WHERE r.code = 'dept_leader'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- dept_leader 部门/科室负责人：共 33 项（第 3/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:profile:signature', 'portal:profile:password', 'portal:profile:session', 'portal:archive', 'portal:archive:search', 'portal:h5', 'flow', 'flow:task:approve')
 WHERE r.code = 'dept_leader'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- dept_leader 部门/科室负责人：共 33 项（第 4/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('flow:task:reject', 'flow:task:addsign', 'flow:task:transfer', 'flow:task:route', 'flow:task:rollback', 'flow:supplement:request', 'flow:task:withdraw', 'flow:print')
 WHERE r.code = 'dept_leader'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- dept_leader 部门/科室负责人：共 33 项（第 5/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('flow:export')
 WHERE r.code = 'dept_leader'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- ===== 角色 branch_leader（分公司分管领导）：33 项 =====
-- 范围：员工基础包 + 审批动作包
-- branch_leader 分公司分管领导：共 33 项（第 1/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:workbench', 'portal:workbench:todo', 'portal:workbench:done', 'portal:workbench:mine', 'portal:workbench:cc', 'portal:initiate', 'portal:initiate:matter', 'portal:initiate:fund')
 WHERE r.code = 'branch_leader'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- branch_leader 分公司分管领导：共 33 项（第 2/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:initiate:contract', 'portal:initiate:seal', 'portal:detail', 'portal:detail:thread', 'portal:detail:attachment', 'portal:detail:print', 'portal:message', 'portal:profile')
 WHERE r.code = 'branch_leader'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- branch_leader 分公司分管领导：共 33 项（第 3/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:profile:signature', 'portal:profile:password', 'portal:profile:session', 'portal:archive', 'portal:archive:search', 'portal:h5', 'flow', 'flow:task:approve')
 WHERE r.code = 'branch_leader'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- branch_leader 分公司分管领导：共 33 项（第 4/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('flow:task:reject', 'flow:task:addsign', 'flow:task:transfer', 'flow:task:route', 'flow:task:rollback', 'flow:supplement:request', 'flow:task:withdraw', 'flow:print')
 WHERE r.code = 'branch_leader'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- branch_leader 分公司分管领导：共 33 项（第 5/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('flow:export')
 WHERE r.code = 'branch_leader'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- ===== 角色 subsidiary_gm（子公司总经理）：33 项 =====
-- 范围：员工基础包 + 审批动作包
-- subsidiary_gm 子公司总经理：共 33 项（第 1/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:workbench', 'portal:workbench:todo', 'portal:workbench:done', 'portal:workbench:mine', 'portal:workbench:cc', 'portal:initiate', 'portal:initiate:matter', 'portal:initiate:fund')
 WHERE r.code = 'subsidiary_gm'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- subsidiary_gm 子公司总经理：共 33 项（第 2/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:initiate:contract', 'portal:initiate:seal', 'portal:detail', 'portal:detail:thread', 'portal:detail:attachment', 'portal:detail:print', 'portal:message', 'portal:profile')
 WHERE r.code = 'subsidiary_gm'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- subsidiary_gm 子公司总经理：共 33 项（第 3/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:profile:signature', 'portal:profile:password', 'portal:profile:session', 'portal:archive', 'portal:archive:search', 'portal:h5', 'flow', 'flow:task:approve')
 WHERE r.code = 'subsidiary_gm'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- subsidiary_gm 子公司总经理：共 33 项（第 4/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('flow:task:reject', 'flow:task:addsign', 'flow:task:transfer', 'flow:task:route', 'flow:task:rollback', 'flow:supplement:request', 'flow:task:withdraw', 'flow:print')
 WHERE r.code = 'subsidiary_gm'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- subsidiary_gm 子公司总经理：共 33 项（第 5/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('flow:export')
 WHERE r.code = 'subsidiary_gm'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- ===== 角色 finance_owner（集团归口（财务部）负责人）：40 项 =====
-- 范围：员工基础包 + 审批动作包 + 财务归口查看项（集团层报表查看，不含报表导出）
-- finance_owner 集团归口（财务部）负责人：共 40 项（第 1/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:workbench', 'portal:workbench:todo', 'portal:workbench:done', 'portal:workbench:mine', 'portal:workbench:cc', 'portal:initiate', 'portal:initiate:matter', 'portal:initiate:fund')
 WHERE r.code = 'finance_owner'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- finance_owner 集团归口（财务部）负责人：共 40 项（第 2/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:initiate:contract', 'portal:initiate:seal', 'portal:detail', 'portal:detail:thread', 'portal:detail:attachment', 'portal:detail:print', 'portal:message', 'portal:profile')
 WHERE r.code = 'finance_owner'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- finance_owner 集团归口（财务部）负责人：共 40 项（第 3/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:profile:signature', 'portal:profile:password', 'portal:profile:session', 'portal:archive', 'portal:archive:search', 'portal:h5', 'flow', 'flow:task:approve')
 WHERE r.code = 'finance_owner'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- finance_owner 集团归口（财务部）负责人：共 40 项（第 4/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('flow:task:reject', 'flow:task:addsign', 'flow:task:transfer', 'flow:task:route', 'flow:task:rollback', 'flow:supplement:request', 'flow:task:withdraw', 'flow:print')
 WHERE r.code = 'finance_owner'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- finance_owner 集团归口（财务部）负责人：共 40 项（第 5/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('flow:export', 'admin:report', 'admin:report:volume', 'admin:report:duration', 'admin:report:reject', 'admin:report:timeout', 'admin:report:backlog', 'admin:report:efficiency')
 WHERE r.code = 'finance_owner'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- ===== 角色 group_leader（集团分管领导）：41 项 =====
-- 范围：员工基础包 + 审批动作包 + 集团层报表查看（不含报表导出）+ **终止流程**（AC-49 / 附录A 权限矩阵）；`portal:detail:*` 已在基础包内
-- group_leader 集团分管领导：共 41 项（第 1/6 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:workbench', 'portal:workbench:todo', 'portal:workbench:done', 'portal:workbench:mine', 'portal:workbench:cc', 'portal:initiate', 'portal:initiate:matter', 'portal:initiate:fund')
 WHERE r.code = 'group_leader'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- group_leader 集团分管领导：共 41 项（第 2/6 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:initiate:contract', 'portal:initiate:seal', 'portal:detail', 'portal:detail:thread', 'portal:detail:attachment', 'portal:detail:print', 'portal:message', 'portal:profile')
 WHERE r.code = 'group_leader'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- group_leader 集团分管领导：共 41 项（第 3/6 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:profile:signature', 'portal:profile:password', 'portal:profile:session', 'portal:archive', 'portal:archive:search', 'portal:h5', 'flow', 'flow:task:approve')
 WHERE r.code = 'group_leader'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- group_leader 集团分管领导：共 41 项（第 4/6 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('flow:task:reject', 'flow:task:addsign', 'flow:task:transfer', 'flow:task:route', 'flow:task:rollback', 'flow:supplement:request', 'flow:task:withdraw', 'flow:task:terminate')
 WHERE r.code = 'group_leader'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- group_leader 集团分管领导：共 41 项（第 5/6 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('flow:print', 'flow:export', 'admin:report', 'admin:report:volume', 'admin:report:duration', 'admin:report:reject', 'admin:report:timeout', 'admin:report:backlog')
 WHERE r.code = 'group_leader'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- group_leader 集团分管领导：共 41 项（第 6/6 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('admin:report:efficiency')
 WHERE r.code = 'group_leader'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- ===== 角色 chairman（集团董事长）：40 项 =====
-- 范围：员工基础包 + 审批动作包 + 集团层报表查看（不含报表导出）；`portal:detail:*` 已在基础包内
-- chairman 集团董事长：共 40 项（第 1/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:workbench', 'portal:workbench:todo', 'portal:workbench:done', 'portal:workbench:mine', 'portal:workbench:cc', 'portal:initiate', 'portal:initiate:matter', 'portal:initiate:fund')
 WHERE r.code = 'chairman'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- chairman 集团董事长：共 40 项（第 2/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:initiate:contract', 'portal:initiate:seal', 'portal:detail', 'portal:detail:thread', 'portal:detail:attachment', 'portal:detail:print', 'portal:message', 'portal:profile')
 WHERE r.code = 'chairman'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- chairman 集团董事长：共 40 项（第 3/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('portal:profile:signature', 'portal:profile:password', 'portal:profile:session', 'portal:archive', 'portal:archive:search', 'portal:h5', 'flow', 'flow:task:approve')
 WHERE r.code = 'chairman'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- chairman 集团董事长：共 40 项（第 4/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('flow:task:reject', 'flow:task:addsign', 'flow:task:transfer', 'flow:task:route', 'flow:task:rollback', 'flow:supplement:request', 'flow:task:withdraw', 'flow:print')
 WHERE r.code = 'chairman'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- chairman 集团董事长：共 40 项（第 5/5 段）
INSERT INTO sys_role_permission (role_id, permission_id, created_by)
SELECT r.id, p.id, NULL
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('flow:export', 'admin:report', 'admin:report:volume', 'admin:report:duration', 'admin:report:reject', 'admin:report:timeout', 'admin:report:backlog', 'admin:report:efficiency')
 WHERE r.code = 'chairman'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- =============================================================================
-- 第 4 部分：自检 SQL（执行完本文件后逐条跑，核对期望值）
-- =============================================================================

-- ① 内置角色数：期望 9
SELECT COUNT(*) AS role_total FROM sys_role;

-- ② 角色齐备性 + 元数据逐行核对：期望 0 行（有行 = 该角色缺失或 role_scope/data_scope 与种子不一致）
SELECT w.code, w.name AS expected_name, w.role_scope AS expected_scope, w.data_scope AS expected_data_scope,
       r.id AS actual_id, r.name AS actual_name, r.role_scope AS actual_scope, r.data_scope AS actual_data_scope
  FROM (
  SELECT 'admin' AS code, '系统管理员' AS name, 'group' AS role_scope, 'group_all' AS data_scope
  UNION ALL SELECT 'company_admin' AS code, '分公司流程管理员' AS name, 'company' AS role_scope, 'company' AS data_scope
  UNION ALL SELECT 'employee' AS code, '普通员工' AS name, 'company' AS role_scope, 'self' AS data_scope
  UNION ALL SELECT 'dept_leader' AS code, '部门/科室负责人' AS name, 'company' AS role_scope, 'dept' AS data_scope
  UNION ALL SELECT 'branch_leader' AS code, '分公司分管领导' AS name, 'company' AS role_scope, 'company' AS data_scope
  UNION ALL SELECT 'subsidiary_gm' AS code, '子公司总经理' AS name, 'company' AS role_scope, 'company' AS data_scope
  UNION ALL SELECT 'finance_owner' AS code, '集团归口（财务部）负责人' AS name, 'group' AS role_scope, 'group_category' AS data_scope
  UNION ALL SELECT 'group_leader' AS code, '集团分管领导' AS name, 'group' AS role_scope, 'group_category' AS data_scope
  UNION ALL SELECT 'chairman' AS code, '集团董事长' AS name, 'group' AS role_scope, 'group_all' AS data_scope
  ) w
  LEFT JOIN sys_role r ON r.code = w.code
 WHERE r.id IS NULL
    OR r.name <> w.name
    OR r.role_scope <> w.role_scope
    OR r.data_scope <> w.data_scope
 ORDER BY w.code;

-- ③ 权限项总数：期望 94
SELECT COUNT(*) AS permission_total FROM sys_permission;

-- ④ 按类型统计：期望 menu=82，button=12，api=0
SELECT perm_type, COUNT(*) AS cnt FROM sys_permission GROUP BY perm_type ORDER BY perm_type;

-- ⑤ 每个角色的授权行数（期望值见下方注释；少于期望值 = 本文件的授权段未执行完）
--   admin           94 行
--   company_admin   38 行
--   employee        24 行
--   dept_leader     33 行
--   branch_leader   33 行
--   subsidiary_gm   33 行
--   finance_owner   40 行
--   group_leader    41 行
--   chairman        40 行
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
  SELECT 'admin' AS code
  UNION ALL SELECT 'company_admin'
  UNION ALL SELECT 'employee'
  UNION ALL SELECT 'dept_leader'
  UNION ALL SELECT 'branch_leader'
  UNION ALL SELECT 'subsidiary_gm'
  UNION ALL SELECT 'finance_owner'
  UNION ALL SELECT 'group_leader'
  UNION ALL SELECT 'chairman'
  ) w
  LEFT JOIN sys_role r ON r.code = w.code
 WHERE r.id IS NULL
 ORDER BY w.code;

-- ⑨ 仅 admin 权限项抽查：全部期望 0
SELECT 'flow:task:reassign 非 admin 授权数（期望 0）' AS check_item, COUNT(*) AS cnt
  FROM sys_role_permission rp
  JOIN sys_role r ON r.id = rp.role_id
  JOIN sys_permission p ON p.id = rp.permission_id
 WHERE p.code = 'flow:task:reassign' AND r.code <> 'admin'
UNION ALL
SELECT 'admin:user:export 非 admin 授权数（期望 0）' AS check_item, COUNT(*) AS cnt
  FROM sys_role_permission rp
  JOIN sys_role r ON r.id = rp.role_id
  JOIN sys_permission p ON p.id = rp.permission_id
 WHERE p.code = 'admin:user:export' AND r.code <> 'admin'
UNION ALL
SELECT 'admin:role:grant 非 admin 授权数（期望 0）' AS check_item, COUNT(*) AS cnt
  FROM sys_role_permission rp
  JOIN sys_role r ON r.id = rp.role_id
  JOIN sys_permission p ON p.id = rp.permission_id
 WHERE p.code = 'admin:role:grant' AND r.code <> 'admin'
UNION ALL
SELECT 'admin:report:export 非 admin 授权数（期望 0）' AS check_item, COUNT(*) AS cnt
  FROM sys_role_permission rp
  JOIN sys_role r ON r.id = rp.role_id
  JOIN sys_permission p ON p.id = rp.permission_id
 WHERE p.code = 'admin:report:export' AND r.code <> 'admin'
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

-- ⑪ group_category 数据域角色的类别配置提醒：'finance_owner', 'group_leader' 期望各 ≥1 行
--    为空 = 该角色看不到任何单据（本文件不播种 sys_role_category，需在「数据域与类别」界面配置）
SELECT r.code AS role_code, COUNT(rc.id) AS category_count
  FROM sys_role r
  LEFT JOIN sys_role_category rc ON rc.role_id = r.id
 WHERE r.data_scope = 'group_category'
 GROUP BY r.code
 ORDER BY r.code;

-- ⑫ 终止流程授权分布（AC-49 / PRD 附录A 权限矩阵「终止流程」行）：期望恰好两行 admin=1、group_leader=1
-- flow:task:terminate 的角色分布：期望恰好两行 —— admin=1、group_leader=1（AC-49 / PRD 附录A 权限矩阵）
SELECT r.code AS role_code, COUNT(*) AS cnt
  FROM sys_role_permission rp
  JOIN sys_role r ON r.id = rp.role_id
  JOIN sys_permission p ON p.id = rp.permission_id
 WHERE p.code = 'flow:task:terminate'
 GROUP BY r.code
 ORDER BY r.code;
