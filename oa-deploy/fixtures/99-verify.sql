-- ============================================================================
-- 99-verify.sql —— 夹具装载后的关键行数断言（**入库、只读**）
-- ----------------------------------------------------------------------------
-- 用途：重置库 → 跑 10/20/30/40/50 → 跑本脚本，一眼看出夹具是否到位。
--       只做 SELECT，不写任何数据。
-- 判定：`verdict` 列为 PASS / FAIL；全部 PASS 才算「库已恢复到可演示状态」。
-- 执行：mysql ... -D oa < oa-deploy/fixtures/99-verify.sql
-- ============================================================================
SET NAMES utf8mb4;

SELECT 'orgs.exdev'      AS check_name, '>=13'  AS expected, CAST(COUNT(*) AS CHAR) AS actual,
       IF(COUNT(*) >= 13, 'PASS', 'FAIL') AS verdict
  FROM sys_org WHERE id IN (1, 12, 13, 135, 136, 137, 138, 150, 151, 152, 153, 154, 155)
UNION ALL
SELECT 'orgs.rt_star',   '=5',    CAST(COUNT(*) AS CHAR),
       IF(COUNT(*) = 5, 'PASS', 'FAIL')
  FROM sys_org WHERE name LIKE 'RT-%'
UNION ALL
SELECT 'orgs.levels',    '=4',    CAST(COUNT(DISTINCT depth) AS CHAR),
       IF(COUNT(DISTINCT depth) = 4, 'PASS', 'FAIL')
  FROM sys_org WHERE id IN (1, 12, 135, 138)
UNION ALL
SELECT 'users.demo',     '=7',    CAST(COUNT(*) AS CHAR),
       IF(COUNT(*) = 7, 'PASS', 'FAIL')
  FROM sys_user WHERE account LIKE 'dev\_%' ESCAPE '\\'
UNION ALL
SELECT 'users.matrix',   '=5',    CAST(COUNT(*) AS CHAR),
       IF(COUNT(*) = 5, 'PASS', 'FAIL')
  FROM sys_user WHERE account LIKE 'mtx\_%' ESCAPE '\\'
UNION ALL
SELECT 'user_position',  '>=9',   CAST(COUNT(*) AS CHAR),
       IF(COUNT(*) >= 9, 'PASS', 'FAIL')
  FROM sys_user_position p JOIN sys_user u ON u.id = p.user_id
 WHERE u.account LIKE 'dev\_%' ESCAPE '\\'
UNION ALL
SELECT 'leader_chain.1', '=1',    CAST(COUNT(*) AS CHAR),
       IF(COUNT(*) = 1, 'PASS', 'FAIL')
  FROM sys_org_leader WHERE org_id = 135 AND leader_type = 'primary' AND category IS NULL
UNION ALL
SELECT 'leader_chain.2', '=1',    CAST(COUNT(*) AS CHAR),
       IF(COUNT(*) = 1, 'PASS', 'FAIL')
  FROM sys_org_leader WHERE org_id = 150 AND leader_type = 'primary'
UNION ALL
SELECT 'leader_chain.3', '=1',    CAST(COUNT(*) AS CHAR),
       IF(COUNT(*) = 1, 'PASS', 'FAIL')
  FROM sys_org_leader WHERE org_id = 12 AND leader_type = 'deputy'
UNION ALL
SELECT 'leader_chain.45','=2',    CAST(COUNT(*) AS CHAR),
       IF(COUNT(*) = 2, 'PASS', 'FAIL')
  FROM sys_org_leader WHERE org_id = 1 AND leader_type = 'primary'
UNION ALL
SELECT 'role_codes',     '=9',    CAST(COUNT(*) AS CHAR),
       IF(COUNT(*) = 9, 'PASS', 'FAIL')
  FROM sys_role
UNION ALL
SELECT 'role.matrix4',   '=4',    CAST(COUNT(DISTINCT r.code) AS CHAR),
       IF(COUNT(DISTINCT r.code) = 4, 'PASS', 'FAIL')
  FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id
 WHERE r.code IN ('company_admin', 'dept_leader', 'group_leader', 'employee')
UNION ALL
SELECT 'trigger.sys_log','=1',    CAST(COUNT(*) AS CHAR),
       IF(COUNT(*) = 1, 'PASS', 'FAIL')
  FROM sys_log WHERE id = 1
UNION ALL
SELECT 'trigger.signature', '=1', CAST(COUNT(*) AS CHAR),
       IF(COUNT(*) = 1, 'PASS', 'FAIL')
  FROM flow_signature WHERE id = 1
UNION ALL
SELECT 'trigger.instance', '=1',  CAST(COUNT(*) AS CHAR),
       IF(COUNT(*) = 1, 'PASS', 'FAIL')
  FROM flow_instance WHERE biz_no = 'OA-2026-000001'
UNION ALL
SELECT 'triggers.total', '=4',    CAST(COUNT(*) AS CHAR),
       IF(COUNT(*) = 4, 'PASS', 'FAIL')
  FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE();
