-- ============================================================================
--  40-authz-matrix.sql —— 阶段 1 DoD「逐接口越权矩阵测试」夹具（**入库、可复现**）
--  来源：移植自 .cache/oa-authz-matrix-fixture.sql（该文件所在目录被 .gitignore 忽略）。
--  用途：为 5 个内置角色各造 1 个可测用户与一条四级组织链，并**占住 sys_user.id=1**
--        （两个 MySQL 集成测试把合成当前登录人写成 userId=1，要求该行真实存在），
--        供 AuthzMatrixMySqlIntegrationTest / AuthzMatrixHttpIT 反复执行（幂等）。
--        **占位行不持有任何角色**（2026-10-04 收敛，理由见文末「角色分配」）——
--        否则节点⑦按 `role_code='admin'` 会把待办解析到不可登录的 id=1 上。
--  执行：见 ./README.md「执行顺序」。
--  说明：手机号为**明文**，用于验证 1.7 的「历史明文一次性迁移」；
--        迁移后本文件重放会把它们写回明文（夹具可重复执行，不影响密文口径验证）。
--  幂等：显式主键 + ON DUPLICATE KEY UPDATE（唯一键：sys_org.id / uk_sys_org_path；
--        sys_user.id / uk_sys_user_account；sys_user_role.uk_sys_user_role）；
--        `matrix_admin`（id=1）走 `WHERE NOT EXISTS` 只补空缺，不覆盖既有行。
--        组织与角色分配与 10/20/30 重叠，重复执行只更新 remark，不新增行。
-- ============================================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------- 组织（四级链）
-- 集团(1) / 公司A(12) / 公司B(13) / 公司A-部门1(135) / 公司A-部门2(136) / 公司B-部门3(137)
INSERT INTO sys_org (id, parent_id, org_type, name, path, depth, sort_no, status, remark) VALUES
  (1,   NULL, 'group',   '集团',   '/1/',        1, 0, 'active', NULL),
  (12,  1,    'company', '公司A',  '/1/12/',     2, 0, 'active', '矩阵夹具'),
  (13,  1,    'company', '公司B',  '/1/13/',     2, 0, 'active', '矩阵夹具'),
  (135, 12,   'dept',    '部门1',  '/1/12/135/', 3, 0, 'active', '矩阵夹具'),
  (136, 12,   'dept',    '部门2',  '/1/12/136/', 3, 0, 'active', '矩阵夹具'),
  (137, 13,   'dept',    '部门3',  '/1/13/137/', 3, 0, 'active', '矩阵夹具')
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), org_type = VALUES(org_type), name = VALUES(name),
  path = VALUES(path), depth = VALUES(depth), status = VALUES(status);

-- ---------------------------------------------------------------- 人员（5 角色各 1 人）
-- 201 分公司管理员(公司A) / 202 部门负责人(公司A-部门1) / 203 集团分管领导(集团)
-- 204 普通员工(公司A-部门1) / 205 普通员工(公司B-部门3)
-- password_hash 是**固定的占位哈希**（矩阵测试走数据域 SQL，不做登录；HTTP 矩阵另建于 API），
-- **不对应任何口令**：它是 '$2a$12$' + 52 个 '0'，合计 **59 字符**，而 BCrypt 密文恒为 60 字符
--   ⇒ 结构上不是合法 BCrypt 密文 ⇒ BCryptPasswordEncoder#matches 对**任何**口令都返回 false
--   （运行期实测：以已知 dev 口令登录这些账号一律 401/40103）。
--   任何「看起来可用」的真实哈希**不得**入库：无从证明它不是某个已知口令的哈希。
--   回归网见 oa-server/src/test/java/com/oa/platform/fixture/FixtureCredentialSafetyTest.java；
--   需要登录时按 ./README.md §6 在运行期 UPDATE（AuthzMatrixHttpTest 就是这么做的）。
INSERT INTO sys_user (id, account, name, employee_no, password_hash, phone, email,
                      org_id, company_id, position, status, remark) VALUES
  (201, 'mtx_ca01', '分公司管理员', 'MTX0001', '$2a$12$0000000000000000000000000000000000000000000000000000',
   '13800000101', 'mtx_ca01@example.com', 12,  12, '流程管理员', 'active', '矩阵夹具'),
  (202, 'mtx_dl01', '部门负责人',   'MTX0002', '$2a$12$0000000000000000000000000000000000000000000000000000',
   '13800000102', 'mtx_dl01@example.com', 135, 12, '部门负责人', 'active', '矩阵夹具'),
  (203, 'mtx_gl01', '集团分管领导', 'MTX0003', '$2a$12$0000000000000000000000000000000000000000000000000000',
   '13800000103', 'mtx_gl01@example.com', 1,   1,  '集团分管领导', 'active', '矩阵夹具'),
  (204, 'mtx_em01', '员工甲',       'MTX0004', '$2a$12$0000000000000000000000000000000000000000000000000000',
   '13800000104', 'mtx_em01@example.com', 135, 12, '专员', 'active', '矩阵夹具'),
  (205, 'mtx_em02', '员工乙',       'MTX0005', '$2a$12$0000000000000000000000000000000000000000000000000000',
   '13800000105', 'mtx_em02@example.com', 137, 13, '专员', 'active', '矩阵夹具')
ON DUPLICATE KEY UPDATE
  name = VALUES(name), employee_no = VALUES(employee_no), phone = VALUES(phone),
  org_id = VALUES(org_id), company_id = VALUES(company_id), status = VALUES(status);

-- ---------------------------------------------------------------- 系统管理员占位行（**id=1**）
-- 为什么需要它：两个 MySQL 集成测试把「当前登录人」写成合成的 **userId = 1**
--   （DataScopeMySqlIntegrationTest:114「SELF 只能看到自己，且目录查询不抛参数异常」、
--    AuthzMatrixMySqlIntegrationTest:171/222「GROUP_ALL 无过滤」），要求库里**真实存在 id=1 的行**。
--   旧环境里 id=1 是最早插入的 bootstrap admin，于是长期「假绿」；**重置库后**夹具不再提供该行，
--   两个用例立刻变红 —— 这正是「夹具不可复现」的形态，故由本夹具**确定性地**占住 id=1。
-- 只补空缺：id=1 或 account 已存在时**整条跳过**，绝不覆盖你手工创建的真实 admin（含其口令/工号）。
INSERT INTO sys_user (id, account, name, employee_no, password_hash, phone, email,
                      org_id, company_id, position, status, remark)
SELECT 1, 'matrix_admin', '系统管理员', 'MTX0000',
       '$2a$12$0000000000000000000000000000000000000000000000000000',
       '13800000100', 'matrix_admin@example.com', 1, 1, '系统管理员', 'active',
       '矩阵夹具：系统管理员占位行（数据域 SQL 层用例的当前登录人 id=1；口令为占位哈希）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 1)
  AND NOT EXISTS (SELECT 1 FROM sys_user WHERE account = 'matrix_admin');

-- ---------------------------------------------------------------- 角色分配
INSERT INTO sys_user_role (user_id, role_id, scope_org_id, remark)
SELECT 201, r.id, 12, '矩阵夹具' FROM sys_role r WHERE r.code = 'company_admin'
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

INSERT INTO sys_user_role (user_id, role_id, scope_org_id, remark)
SELECT 202, r.id, 135, '矩阵夹具' FROM sys_role r WHERE r.code = 'dept_leader'
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

INSERT INTO sys_user_role (user_id, role_id, scope_org_id, remark)
SELECT 203, r.id, NULL, '矩阵夹具' FROM sys_role r WHERE r.code = 'group_leader'
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

INSERT INTO sys_user_role (user_id, role_id, scope_org_id, remark)
SELECT 204, r.id, NULL, '矩阵夹具' FROM sys_role r WHERE r.code = 'employee'
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

INSERT INTO sys_user_role (user_id, role_id, scope_org_id, remark)
SELECT 205, r.id, NULL, '矩阵夹具' FROM sys_role r WHERE r.code = 'employee'
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

-- 系统管理员也纳入矩阵：确保 GROUP_ALL 口径确实无过滤 —— **只授予可登录的真实管理员**
-- （`account = 'admin'`，本机由 90-dev-admin.md 建）。
--
-- 占位行 `sys_user.id = 1`（`matrix_admin`，占位哈希、**不可登录**）**刻意不授予任何角色**
-- （2026-10-04 收敛，此前有一条 `WHERE u.id = 1` 的 admin 授权）：
--   · 它的存在理由是「两个 MySQL 集成测试把合成的当前登录人写成 userId = 1，要求该行真实存在」。
--     那两个测试的**数据域与角色上下文都在进程内合成**（各自私有的 `authenticate(...)` 直接构造
--     `CurrentUser` + `DataScopeContext`），**从不回读 `sys_user_role`** —— 因此该行**有没有角色
--     都不影响任何断言**（见 AuthzMatrixMySqlIntegrationTest:167/314、DataScopeMySqlIntegrationTest:103）。
--   · 而节点⑦（`archive_register`，`approver_param = {"role_code":"admin"}`）是**按角色解析候选人**的：
--     占位行一旦持有 admin 角色，⑦ 就解析出 `[1(matrix_admin), <真实 admin>]`，待办落在**不可登录**的
--     id=1 上 —— 演示与验收必须先以系统管理员「改派」才能继续。那是夹具自己制造的人工障碍。
--     收敛后 ⑦ 只解析到真实可登录的 `account='admin'`。
--   · 授权**只收敛到可登录账号**，与「占位行必须存在」这条硬依赖**互不冲突**：两件事由本文件分别满足。
--
-- 幂等提示：本夹具只 INSERT/UPDATE、**从不 DELETE**，因此这次收敛**不会撤销**任何既有环境里
-- id=1 已经获得的角色（老库需要重置库才会看到收敛效果）。
INSERT INTO sys_user_role (user_id, role_id, scope_org_id, remark)
SELECT u.id, r.id, NULL, '矩阵夹具' FROM sys_user u JOIN sys_role r ON r.code = 'admin'
WHERE u.account = 'admin'
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

-- ---------------------------------------------------------------- 自检
SELECT '40-authz-matrix' AS fixture,
       (SELECT COUNT(*) FROM sys_user WHERE account LIKE 'mtx\_%') AS mtx_users,
       (SELECT COUNT(*) FROM sys_user WHERE id = 1) AS user_id_1_rows,
       (SELECT COUNT(*) FROM sys_org  WHERE id IN (1, 12, 13, 135, 136, 137)) AS mtx_orgs;
