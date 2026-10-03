-- ============================================================================
-- 10-dev-orgs.sql —— 开发/演示组织架构（**入库、可复现**）
-- ----------------------------------------------------------------------------
-- 作用：Flyway V1~V4 只播种 角色 / 权限 / 字典 / 流程模板，**不播种任何组织**。
--       重置库后若不跑本脚本，组织树为空、四类单据因候选人为空而 precheck 失败。
--       本脚本补齐「集团 → 公司 → 部门 → 科室」四级演示组织，并保留上一轮被
--       重置库弄丢的 `RT-*` 运行期样例组织语义（**沿用 RT- 前缀**，与种子/演示组织区分）。
--
-- 幂等：全部使用显式主键 + `INSERT ... ON DUPLICATE KEY UPDATE`。
--       唯一键：`sys_org.PRIMARY KEY (id)` 与 `sys_org.uk_sys_org_path (path)`。
--       重复执行结果完全一致（不新增行、不改变 id）。
-- 前置：无（可在 Flyway V1~V4 之后立刻执行）。
-- 后续：20-dev-people.sql（人员与负责人链）→ 30-dev-roles.sql → 40/50。
-- 执行：见 ./README.md「执行顺序」。
--
-- 保留 id 段（本目录约定，勿与其它种子冲突）：
--   1            集团根节点
--   12 / 13      公司A / 公司B
--   135 / 136    公司A 的部门1 / 部门2
--   137          公司B 的部门3
--   138          部门1 下的科室1（凑满四级）
--   150          集团财务部（审批人解析 ② 的归口部门）
--   151~155      RT-* 运行期样例组织（沿用历史数据语义）
-- ============================================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------- 1) 集团根节点
INSERT INTO sys_org (id, parent_id, org_type, name, path, depth, sort_no, status, remark)
VALUES (1, NULL, 'group', '集团', '/1/', 1, 0, 'active', 'dev 夹具：集团根节点')
ON DUPLICATE KEY UPDATE
  org_type = VALUES(org_type), name = VALUES(name), path = VALUES(path),
  depth = VALUES(depth), sort_no = VALUES(sort_no), status = VALUES(status),
  deleted_at = NULL;

-- ---------------------------------------------------------------- 2) 公司层（2 家）
INSERT INTO sys_org (id, parent_id, org_type, name, path, depth, sort_no, status, remark) VALUES
  (12, 1, 'company', '公司A', '/1/12/', 2, 10, 'active', 'dev 夹具'),
  (13, 1, 'company', '公司B', '/1/13/', 2, 20, 'active', 'dev 夹具')
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), org_type = VALUES(org_type), name = VALUES(name),
  path = VALUES(path), depth = VALUES(depth), sort_no = VALUES(sort_no),
  status = VALUES(status), deleted_at = NULL;

-- ---------------------------------------------------------------- 3) 部门层
INSERT INTO sys_org (id, parent_id, org_type, name, path, depth, sort_no, status, remark) VALUES
  (135, 12, 'dept', '部门1',  '/1/12/135/', 3, 10, 'active', 'dev 夹具：公司A 部门1'),
  (136, 12, 'dept', '部门2',  '/1/12/136/', 3, 20, 'active', 'dev 夹具：公司A 部门2'),
  (137, 13, 'dept', '部门3',  '/1/13/137/', 3, 10, 'active', 'dev 夹具：公司B 部门3'),
  (150, 1,  'dept', '财务部', '/1/150/',    3, 30, 'active', 'dev 夹具：集团归口部门（审批人解析 ② finance_owner 按此名定位）')
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), org_type = VALUES(org_type), name = VALUES(name),
  path = VALUES(path), depth = VALUES(depth), sort_no = VALUES(sort_no),
  status = VALUES(status), deleted_at = NULL;

-- ---------------------------------------------------------------- 4) 科室层（第四级）
INSERT INTO sys_org (id, parent_id, org_type, name, path, depth, sort_no, status, remark) VALUES
  (138, 135, 'section', '科室1', '/1/12/135/138/', 4, 10, 'active', 'dev 夹具：四级链最末一级（部门负责人上溯解析用）')
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), org_type = VALUES(org_type), name = VALUES(name),
  path = VALUES(path), depth = VALUES(depth), sort_no = VALUES(sort_no),
  status = VALUES(status), deleted_at = NULL;

-- ---------------------------------------------------------------- 5) RT-* 运行期样例组织
-- 语义（沿用被重置库弄丢的那批数据）：一条公司→部门→科室的完整 RT 链、
-- 一条挂在公司A 下的 RT 部门，以及**同一父节点下两个同名 RT-Dept**
-- （用于复现「同父同名只告警不阻断」这一预期行为，见 oa-deploy/LOCAL-DEV.md §7）。
INSERT INTO sys_org (id, parent_id, org_type, name, path, depth, sort_no, status, remark) VALUES
  (151, 1,   'company', 'RT-Company',              '/1/151/',         2, 90, 'active', 'RT 夹具：运行期新建公司'),
  (152, 151, 'dept',    'RT-Dept',                 '/1/151/152/',     3, 10, 'active', 'RT 夹具：公司下部门'),
  (153, 152, 'section', 'RT-Section',              '/1/151/152/153/', 4, 10, 'active', 'RT 夹具：部门下科室'),
  (154, 12,  'dept',    'RT-Dept-Under-CompanyA',  '/1/12/154/',      3, 90, 'active', 'RT 夹具：挂在公司A 下的部门'),
  (155, 151, 'dept',    'RT-Dept',                 '/1/151/155/',     3, 20, 'active', 'RT 夹具：与 152 同父同名（同父同名只告警不阻断）')
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), org_type = VALUES(org_type), name = VALUES(name),
  path = VALUES(path), depth = VALUES(depth), sort_no = VALUES(sort_no),
  status = VALUES(status), deleted_at = NULL;

-- ---------------------------------------------------------------- 6) 自检
SELECT '10-dev-orgs' AS fixture, COUNT(*) AS org_rows_now FROM sys_org;
SELECT id, parent_id, org_type, depth, path, name
  FROM sys_org WHERE id IN (1, 12, 13, 135, 136, 137, 138, 150, 151, 152, 153, 154, 155)
 ORDER BY id;
