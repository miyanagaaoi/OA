-- ============================================================================
--  40-authz-matrix.sql —— 阶段 1 DoD「逐接口越权矩阵测试」夹具（**入库、可复现**）
--  来源：移植自 .cache/oa-authz-matrix-fixture.sql（该文件所在目录被 .gitignore 忽略）。
--  用途：为 5 个内置角色各造 1 个可测用户与一条四级组织链，
--        供 AuthzMatrixMySqlIntegrationTest / AuthzMatrixHttpIT 反复执行（幂等）。
--  执行：见 ./README.md「执行顺序」。
--  说明：手机号为**明文**，用于验证 1.7 的「历史明文一次性迁移」；
--        迁移后本文件重放会把它们写回明文（夹具可重复执行，不影响密文口径验证）。
--  幂等：显式主键 + ON DUPLICATE KEY UPDATE（唯一键：sys_org.id / uk_sys_org_path；
--        sys_user.id / uk_sys_user_account；sys_user_role.uk_sys_user_role）。
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
-- **不对应任何口令**；需要登录时按 ./90-dev-admin.md 自行 UPDATE 一个你自己生成的哈希。
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

-- 系统管理员（既有 admin）也纳入矩阵：确保 GROUP_ALL 口径确实无过滤
INSERT INTO sys_user_role (user_id, role_id, scope_org_id, remark)
SELECT u.id, r.id, NULL, '矩阵夹具' FROM sys_user u JOIN sys_role r ON r.code = 'admin'
WHERE u.account = 'admin'
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

-- ---------------------------------------------------------------- 自检
SELECT '40-authz-matrix' AS fixture,
       (SELECT COUNT(*) FROM sys_user WHERE account LIKE 'mtx\_%') AS mtx_users,
       (SELECT COUNT(*) FROM sys_org  WHERE id IN (1, 12, 13, 135, 136, 137)) AS mtx_orgs;
