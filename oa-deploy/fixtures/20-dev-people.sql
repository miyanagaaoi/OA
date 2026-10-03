-- ============================================================================
-- 20-dev-people.sql —— 开发/演示人员 + 一人多岗 + 负责人链（**入库、可复现**）
-- ----------------------------------------------------------------------------
-- 作用：让四类单据的审批人 precheck 能跑到 allowed=true（①~⑥ 候选人均可解析），
--       并提供覆盖各内置角色的演示账号。
--
-- 负责人链与解析规则的对应（实现见 oa-server .../workflow/approver/app/rules/*）：
--   ① dept_leader_upward -> sys_org_leader(135,'primary',category IS NULL) = 302
--   ② finance_owner      -> sys_org_leader(150,'primary')                 = 301
--   ③ branch_leader      -> sys_org_leader(12, 'deputy')                  = 302
--   ④ subsidiary_gm      -> sys_org_leader(12, 'primary')                 = 301
--   ⑤ group_leader       -> sys_org_leader(1,  'primary','economy')       = 303
--   ⑥ chairman           -> sys_org_leader(1,  'primary', NULL)           = 303
--   ⑦ archive_register   -> role_code='admin'（无需夹具）
--
-- 一人多岗（sys_user_position）：301 同时是 公司A正职 与 财务部正职；
--   302 同时是 部门1负责人 与 公司A副职；306 是 科室1负责人。
--
-- 口令：本文件**不写入任何可用口令**。演示账号的 password_hash 是一个
--       语法合法但**不对应任何口令**的占位哈希（`$2a$12$000…`）。
--       需要以某个演示账号登录时，按 ./90-dev-admin.md 生成你自己的哈希后
--       执行那条 UPDATE（同样适用于 admin）。
--
-- 幂等：显式主键 + `INSERT ... ON DUPLICATE KEY UPDATE`。
--   唯一键：`sys_user.PRIMARY KEY (id)` / `sys_user.uk_sys_user_account (account)`；
--           `sys_user_position.uk_user_org (user_id, org_id)`；
--           `sys_org_leader.uk_org_leader (org_id, user_id, leader_type, category_key)`
--           （`category_key` = `IFNULL(category,'')` 的生成列，见 doc/data-model.md §2.3，
--            故 NULL 与 NULL 视为同一组、可安全 upsert）。
-- 前置：10-dev-orgs.sql；Flyway V1~V4（角色/权限已播种）。
-- 执行：见 ./README.md「执行顺序」。
--
-- 保留 id 段：301~307（演示人员）。
-- ============================================================================
SET NAMES utf8mb4;

-- 占位哈希：BCrypt 形状的 60 字符串，**不是任何口令的有效哈希**（登录必然失败）。
SET @placeholder_hash = '$2a$12$0000000000000000000000000000000000000000000000000000';

-- ---------------------------------------------------------------- 1) 演示人员
-- 301 分公司管理员（公司A 正职 / 财务部正职）  302 部门负责人（部门1 / 公司A 副职）
-- 303 集团分管领导（集团正职 / 经济线）        304 员工甲（公司A 部门1）
-- 305 员工乙（公司B 部门3）                    306 科室1 负责人
-- 307 财务专员（集团财务部）
INSERT INTO sys_user (id, account, name, employee_no, password_hash, phone, email,
                      org_id, company_id, position, status, remark) VALUES
  (301, 'dev_ca01',  '分公司管理员', 'DEV0001', @placeholder_hash, '13800000201', 'dev_ca01@example.com',  12,  12, '公司A 总经理',  'active', 'dev 夹具：公司正职 + 财务部正职'),
  (302, 'dev_dl01',  '部门负责人',   'DEV0002', @placeholder_hash, '13800000202', 'dev_dl01@example.com',  135, 12, '部门1 负责人',  'active', 'dev 夹具：部门负责人 + 公司A 副职'),
  (303, 'dev_gl01',  '集团分管领导', 'DEV0003', @placeholder_hash, '13800000203', 'dev_gl01@example.com',  1,   1,  '集团董事长',    'active', 'dev 夹具：集团正职 + 经济线分管'),
  (304, 'dev_em01',  '员工甲',       'DEV0004', @placeholder_hash, '13800000204', 'dev_em01@example.com',  135, 12, '专员',          'active', 'dev 夹具：普通员工（公司A）'),
  (305, 'dev_em02',  '员工乙',       'DEV0005', @placeholder_hash, '13800000205', 'dev_em02@example.com',  137, 13, '专员',          'active', 'dev 夹具：普通员工（公司B）'),
  (306, 'dev_sc01',  '科室1负责人',  'DEV0006', @placeholder_hash, '13800000206', 'dev_sc01@example.com',  138, 12, '科室1 负责人',  'active', 'dev 夹具：四级链最末一级负责人'),
  (307, 'dev_fo01',  '财务专员',     'DEV0007', @placeholder_hash, '13800000207', 'dev_fo01@example.com',  150, 1,  '财务会计',      'active', 'dev 夹具：集团财务部专员')
ON DUPLICATE KEY UPDATE
  name        = VALUES(name),
  employee_no = VALUES(employee_no),
  phone       = VALUES(phone),
  email       = VALUES(email),
  org_id      = VALUES(org_id),
  company_id  = VALUES(company_id),
  position    = VALUES(position),
  status      = VALUES(status),
  deleted_at  = NULL;
  -- 刻意不更新 password_hash：重复执行不会覆盖你按 90-dev-admin.md 设置的登录口令。

-- ---------------------------------------------------------------- 2) 一人多岗
-- 子行**按 account 取真实 user_id**：若某个 account 已存在但 id 与预期不同
-- （例如先跑过旧版夹具），这里取到的仍是库里那一行的 id，不会产生外键悬空。
SET @u_ca01 = (SELECT id FROM sys_user WHERE account = 'dev_ca01' LIMIT 1);
SET @u_dl01 = (SELECT id FROM sys_user WHERE account = 'dev_dl01' LIMIT 1);
SET @u_gl01 = (SELECT id FROM sys_user WHERE account = 'dev_gl01' LIMIT 1);
SET @u_em01 = (SELECT id FROM sys_user WHERE account = 'dev_em01' LIMIT 1);
SET @u_em02 = (SELECT id FROM sys_user WHERE account = 'dev_em02' LIMIT 1);
SET @u_sc01 = (SELECT id FROM sys_user WHERE account = 'dev_sc01' LIMIT 1);
SET @u_fo01 = (SELECT id FROM sys_user WHERE account = 'dev_fo01' LIMIT 1);

INSERT INTO sys_user_position (user_id, org_id, is_primary, position, remark) VALUES
  (@u_ca01, 12,  1, '公司A 总经理',  'dev 夹具：主岗'),
  (@u_ca01, 150, 0, '财务部负责人',  'dev 夹具：兼岗（归口部门正职）'),
  (@u_dl01, 135, 1, '部门1 负责人',  'dev 夹具：主岗'),
  (@u_dl01, 12,  0, '公司A 分管领导','dev 夹具：兼岗（公司副职）'),
  (@u_gl01, 1,   1, '集团董事长',    'dev 夹具：主岗'),
  (@u_em01, 135, 1, '专员',          'dev 夹具：主岗'),
  (@u_em02, 137, 1, '专员',          'dev 夹具：主岗'),
  (@u_sc01, 138, 1, '科室1 负责人',  'dev 夹具：主岗'),
  (@u_fo01, 150, 1, '财务会计',      'dev 夹具：主岗')
ON DUPLICATE KEY UPDATE
  is_primary = VALUES(is_primary), position = VALUES(position), remark = VALUES(remark);

-- ---------------------------------------------------------------- 3) 负责人链（审批人解析的唯一权威来源）
-- 幂等写法：**直接 upsert**（不再需要「归一重复 → 清空 → 重建」特例）。
--   `sys_org_leader.uk_org_leader (org_id, user_id, leader_type, category_key)` 的 `category_key`
--   是 `category` 的生成列（`IFNULL(category,'')`，见 doc/data-model.md §2.3）——
--   这修掉了「唯一键含可空列 `category`、MySQL 唯一索引对 NULL 不去重 → 重复执行不断堆积重复负责人行」
--   的缺陷（业务语义不变：NULL 与 NULL 同组、非 NULL 仍按值区分）。
--   **归属约定**：`sys_org_leader` 中 org_id ∈ {1, 12, 135, 138, 150} 的行由本夹具独占
--   （这五个组织也由 10-dev-orgs.sql 定义）；如需手工加负责人，请用别的组织节点。
INSERT INTO sys_org_leader (org_id, user_id, leader_type, duty_title, category, sort_no, remark) VALUES
  (135, @u_dl01, 'primary', '部门1 负责人',   NULL,      0, '① dept_leader_upward'),
  (138, @u_sc01, 'primary', '科室1 负责人',   NULL,      0, '① 上溯起点（科室已设负责人时直接命中）'),
  (12,  @u_ca01, 'primary', '公司A 总经理',   NULL,      0, '④ subsidiary_gm'),
  (12,  @u_dl01, 'deputy',  '公司A 分管领导', NULL,      0, '③ branch_leader'),
  (1,   @u_gl01, 'primary', '集团董事长',     NULL,      0, '⑥ chairman'),
  (1,   @u_gl01, 'primary', '集团分管领导',   'economy', 0, '⑤ group_leader（经济线）'),
  (150, @u_ca01, 'primary', '财务部负责人',   NULL,      0, '② finance_owner')
ON DUPLICATE KEY UPDATE
  duty_title = VALUES(duty_title),
  sort_no    = VALUES(sort_no),
  remark     = VALUES(remark);

-- ---------------------------------------------------------------- 4) 冗余主负责人回填（sys_org.leader_id）
UPDATE sys_org o
   SET o.leader_id = (SELECT l.user_id FROM sys_org_leader l
                       WHERE l.org_id = o.id AND l.leader_type = 'primary' AND l.category IS NULL
                       ORDER BY l.sort_no, l.id LIMIT 1)
 WHERE o.id IN (1, 12, 135, 138, 150);

-- ---------------------------------------------------------------- 5) 自检
SELECT '20-dev-people' AS fixture,
       (SELECT COUNT(*) FROM sys_user)         AS users_now,
       (SELECT COUNT(*) FROM sys_user_position) AS positions_now,
       (SELECT COUNT(*) FROM sys_org_leader)   AS leaders_now;

SELECT l.org_id, o.name AS org_name, l.leader_type, l.category, l.user_id, u.name AS user_name
  FROM sys_org_leader l
  JOIN sys_org  o ON o.id = l.org_id
  JOIN sys_user u ON u.id = l.user_id
 WHERE l.org_id IN (1, 12, 135, 138, 150)
 ORDER BY l.org_id, l.leader_type, l.category;
