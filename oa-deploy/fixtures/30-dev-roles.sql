-- ============================================================================
-- 30-dev-roles.sql —— 演示账号 → 9 个内置角色（**入库、可复现**）
-- ----------------------------------------------------------------------------
-- 作用：把 20-dev-people.sql 的演示账号挂到 Flyway V4 播种的 9 个内置角色上，
--       覆盖矩阵/越权测试需要的 company_admin / dept_leader / group_leader /
--       employee 四类，并顺带覆盖 branch_leader / subsidiary_gm / finance_owner /
--       chairman，使「登录后看到的数据域差异」可演示。
--
-- 角色（V4 播种，data_scope 见括号）：
--   admin(group_all) company_admin(company) employee(self) dept_leader(dept)
--   branch_leader(company) subsidiary_gm(company) finance_owner(group_category)
--   group_leader(group_category) chairman(group_all)
--
-- 幂等：**按 account 查 user_id**（不写死 id）+ `ON DUPLICATE KEY UPDATE`。
--   这样即使某个 account 已存在、但 id 与预期不同（例如先跑过旧版夹具），
--   本脚本也只更新那一行，不会留下指向不存在用户的子行。
--   唯一键：`sys_user_role.uk_sys_user_role (user_id, role_id, scope_org_key)`
--   —— scope_org_id 为 NULL 时按 0 参与唯一键（生成列 scope_org_key）。
-- 前置：10-dev-orgs.sql、20-dev-people.sql、Flyway V4。
-- ============================================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------- 1) 演示人员角色
INSERT INTO sys_user_role (user_id, role_id, scope_org_id, remark)
SELECT u.id, r.id, 12, 'dev 夹具：公司A 流程管理员'
  FROM sys_user u JOIN sys_role r ON r.code = 'company_admin'
 WHERE u.account = 'dev_ca01'
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

INSERT INTO sys_user_role (user_id, role_id, scope_org_id, remark)
SELECT u.id, r.id, 12, 'dev 夹具：公司A 总经理'
  FROM sys_user u JOIN sys_role r ON r.code = 'subsidiary_gm'
 WHERE u.account = 'dev_ca01'
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

INSERT INTO sys_user_role (user_id, role_id, scope_org_id, remark)
SELECT u.id, r.id, 150, 'dev 夹具：财务部负责人'
  FROM sys_user u JOIN sys_role r ON r.code = 'finance_owner'
 WHERE u.account = 'dev_ca01'
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

INSERT INTO sys_user_role (user_id, role_id, scope_org_id, remark)
SELECT u.id, r.id, 135, 'dev 夹具：部门1 负责人'
  FROM sys_user u JOIN sys_role r ON r.code = 'dept_leader'
 WHERE u.account = 'dev_dl01'
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

INSERT INTO sys_user_role (user_id, role_id, scope_org_id, remark)
SELECT u.id, r.id, 12, 'dev 夹具：公司A 分管领导'
  FROM sys_user u JOIN sys_role r ON r.code = 'branch_leader'
 WHERE u.account = 'dev_dl01'
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

INSERT INTO sys_user_role (user_id, role_id, scope_org_id, remark)
SELECT u.id, r.id, NULL, 'dev 夹具：集团分管领导'
  FROM sys_user u JOIN sys_role r ON r.code = 'group_leader'
 WHERE u.account = 'dev_gl01'
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

INSERT INTO sys_user_role (user_id, role_id, scope_org_id, remark)
SELECT u.id, r.id, NULL, 'dev 夹具：集团董事长'
  FROM sys_user u JOIN sys_role r ON r.code = 'chairman'
 WHERE u.account = 'dev_gl01'
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

INSERT INTO sys_user_role (user_id, role_id, scope_org_id, remark)
SELECT u.id, r.id, NULL, 'dev 夹具：普通员工（公司A）'
  FROM sys_user u JOIN sys_role r ON r.code = 'employee'
 WHERE u.account = 'dev_em01'
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

INSERT INTO sys_user_role (user_id, role_id, scope_org_id, remark)
SELECT u.id, r.id, NULL, 'dev 夹具：普通员工（公司B）'
  FROM sys_user u JOIN sys_role r ON r.code = 'employee'
 WHERE u.account = 'dev_em02'
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

INSERT INTO sys_user_role (user_id, role_id, scope_org_id, remark)
SELECT u.id, r.id, 138, 'dev 夹具：科室1 负责人'
  FROM sys_user u JOIN sys_role r ON r.code = 'dept_leader'
 WHERE u.account = 'dev_sc01'
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

INSERT INTO sys_user_role (user_id, role_id, scope_org_id, remark)
SELECT u.id, r.id, NULL, 'dev 夹具：财务专员'
  FROM sys_user u JOIN sys_role r ON r.code = 'employee'
 WHERE u.account = 'dev_fo01'
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

-- ---------------------------------------------------------------- 2) 现有 admin（若已按 90-dev-admin.md 创建）纳入演示
INSERT INTO sys_user_role (user_id, role_id, scope_org_id, remark)
SELECT u.id, r.id, NULL, 'dev 夹具：系统管理员'
  FROM sys_user u JOIN sys_role r ON r.code = 'admin'
 WHERE u.account = 'admin'
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

-- ---------------------------------------------------------------- 3) 自检：角色 × 人数
SELECT r.code, COUNT(ur.id) AS holders
  FROM sys_role r LEFT JOIN sys_user_role ur ON ur.role_id = r.id
 GROUP BY r.id, r.code
 ORDER BY r.id;
