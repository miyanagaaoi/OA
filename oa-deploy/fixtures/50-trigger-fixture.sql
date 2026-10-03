-- ============================================================================
-- 50-trigger-fixture.sql —— 不可篡改触发器「验收夹具」（**入库、可复现**）
-- 来源：移植自 .cache/trigger-test-fixture.sql（该文件所在目录被 .gitignore 忽略）。
-- ----------------------------------------------------------------------------
-- 为什么需要它：
--   MySQL 的 BEFORE UPDATE / BEFORE DELETE 触发器是 **逐行触发** 的。
--   在空表上执行 `UPDATE sys_log SET action='x' WHERE id=1;` 命中 0 行，
--   触发器根本不会被调用 → 语句"成功返回 0 rows affected"，产生**假通过**（AC-20）。
--   因此必须先真实存在 id=1 的行，验收才有意义。
--
-- 内容：
--   sys_log        : id=1 一行（只追加表，INSERT 不受触发器限制）
--   form_data      : 一行（flow_instance.form_data_id 的非空外键目标）
--   flow_instance  : 一行（flow_signature.instance_id 的非空外键目标）
--   flow_signature : id=1 一行（signature.hash 为 CHAR(64) NOT NULL）
--
-- 幂等：`INSERT ... SELECT ... WHERE NOT EXISTS`，已存在则跳过；
--   唯一键：sys_log.id、form_data.uk_form_data_biz_no、
--           flow_instance.uk_flow_instance_biz_no、flow_signature.id。
-- 前置：10-dev-orgs.sql、20-dev-people.sql（需要至少 1 个 sys_user 供外键）、Flyway V3（flow_template）。
-- 执行：见 ./README.md「执行顺序」。
-- ============================================================================
SET NAMES utf8mb4;

-- 夹具操作人：优先 admin（按 90-dev-admin.md 创建），否则退化为第一个在职用户。
SET @fixture_user_id = IFNULL(
  (SELECT id FROM sys_user WHERE account = 'admin' LIMIT 1),
  (SELECT id FROM sys_user WHERE status = 'active' ORDER BY id LIMIT 1));

-- ------------------------------------------------------- 1) sys_log id=1
INSERT INTO sys_log (id, user_id, user_name, action, target_type, target_id, after_json, ip, user_agent)
SELECT 1, @fixture_user_id, '系统管理员', 'login', 'user', @fixture_user_id,
       '{"fixture":"immutable-trigger-acceptance"}', '127.0.0.1', 'fixture'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_log WHERE id = 1);

-- ------------------------------------------------------- 2) form_data
INSERT INTO form_data (form_type, biz_no, fields_json, schema_version, creator_id)
SELECT 'matter', 'OA-2026-000001', '{"fixture":"immutable-trigger-acceptance"}', 1, @fixture_user_id
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM form_data WHERE biz_no = 'OA-2026-000001');

SET @fd_id = (SELECT id FROM form_data WHERE biz_no = 'OA-2026-000001' LIMIT 1);

-- ------------------------------------------------------- 3) flow_instance
INSERT INTO flow_instance (biz_no, template_id, template_version, form_data_id, form_type, category,
                           initiator_id, initiator_org_id, initiator_company_id, initiator_org_path,
                           approver_snapshot_json, status)
SELECT 'OA-2026-000001',
       (SELECT id FROM flow_template WHERE code = 'matter' ORDER BY id LIMIT 1),
       1, @fd_id, 'matter', 'admin',
       @fixture_user_id, 1, 1, '/1/',
       '{"fixture":"immutable-trigger-acceptance"}', 'draft'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM flow_instance WHERE biz_no = 'OA-2026-000001');

SET @inst_id = (SELECT id FROM flow_instance WHERE biz_no = 'OA-2026-000001' LIMIT 1);

-- ------------------------------------------------------- 4) flow_signature id=1
INSERT INTO flow_signature (id, instance_id, task_id, user_id, sign_type, sign_image, signed_at,
                            device_fingerprint, ip, user_agent, hash)
SELECT 1, @inst_id, NULL, @fixture_user_id, 'handwrite', NULL, NOW(),
       'fixture-device', '127.0.0.1', 'fixture', REPEAT('a', 64)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM flow_signature WHERE id = 1);

-- ------------------------------------------------------- 5) 自检
SELECT CONCAT('sys_log        id=1 rows=', (SELECT COUNT(*) FROM sys_log WHERE id = 1))         AS fixture_sys_log;
SELECT CONCAT('flow_signature id=1 rows=', (SELECT COUNT(*) FROM flow_signature WHERE id = 1))  AS fixture_signature;
SELECT CONCAT('flow_instance  id=', @inst_id, ' biz_no=OA-2026-000001')                         AS fixture_instance;
