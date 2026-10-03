-- ============================================================================
-- V5 · Q6 / Q7 闸门配置列（产品裁定 2026-10-03：两者均为**可配置项**）
-- ----------------------------------------------------------------------------
-- 背景：V0.4 曾把 Q6（流转/回退/补件的次数上限）与 Q7（补件时限与超时处理）**定稿为固定值**
--       （流转+回退 <=5、同节点被回退 <=2、回到本部门连续 <=2、补件同节点 <=1 且全单 <=3；
--        时限 3 个工作日、超时仅催办）。2026-10-03 产品复议：**改为可配置项**，
--       V0.4 的数值降级为「默认值」，默认行为不变。
--
-- 落点：模板级（flow_template）—— 理由是这两项都是「全单累计」语义
--       （data-model.md §5.1 的 routing_count / supplement_count 就是**实例级**计数），
--       而非节点级；节点级的时间维度已由 flow_node.timeout_hours 承担（T-01）。
--       因此本次**只做模板级**，不做节点级覆盖（避免引入未裁定的优先级规则）。
--
-- 语义（应用层实现，见 com.oa.workflow.definition.domain.FlowGatePolicy）：
--   · max_return_count         NULL 或 0 = **不限**；1..99 = 上限（>99 拒绝，防空转）
--   · max_supplement_count     NULL 或 0 = **不限**；1..99 = 上限
--   · supplement_deadline_days NULL = **不设时限**；1..365 = 生效（0 与负数一律拒绝）
--   · supplement_deadline_type calendar 自然日 / working 工作日（默认 working）
--   · on_supplement_timeout    notify 仅提醒（默认，与 V0.4 一致）/ auto_pass 自动通过 /
--                              auto_return 自动退回
--
-- 安全性：**只新增可空列与一条 CHECK 约束**，不改动任何既有列/索引/约束，对存量数据零影响；
--         在途实例仍然通过 flow_instance.template_version 锁定版本（AC-09），本迁移与之无关。
--
-- TODO(2a.4): 按 max_return_count / max_supplement_count 判定（本迁移只落配置）
-- TODO(阶段3): 按 supplement_deadline_days/type 与 on_supplement_timeout 调度（本迁移只落配置）
-- ============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 新增 5 个可空列（Q6 两项 + Q7 三项）
-- ---------------------------------------------------------------------------
ALTER TABLE flow_template
  ADD COLUMN max_return_count         INT          NULL COMMENT 'Q6 回退次数上限（NULL 或 0 = 不限；1..99）',
  ADD COLUMN max_supplement_count     INT          NULL COMMENT 'Q6 补件次数上限（NULL 或 0 = 不限；1..99）',
  ADD COLUMN supplement_deadline_days INT          NULL COMMENT 'Q7 补件时限天数（NULL = 不设时限；1..365）',
  ADD COLUMN supplement_deadline_type VARCHAR(16)  NULL COMMENT 'Q7 补件时限口径：calendar 自然日 / working 工作日',
  ADD COLUMN on_supplement_timeout    VARCHAR(16)  NULL COMMENT 'Q7 补件超时处理：notify 仅提醒 / auto_pass 自动通过 / auto_return 自动退回';

-- ---------------------------------------------------------------------------
-- 2. 取值范围 CHECK（MySQL 8.0.16+ 生效；NULL 一律放行 = 「不限」）
-- ---------------------------------------------------------------------------
ALTER TABLE flow_template
  ADD CONSTRAINT chk_flow_template_gates CHECK (
    (max_return_count         IS NULL OR max_return_count         BETWEEN 0 AND 99)   AND
    (max_supplement_count     IS NULL OR max_supplement_count     BETWEEN 0 AND 99)   AND
    (supplement_deadline_days IS NULL OR supplement_deadline_days BETWEEN 1 AND 365)  AND
    (supplement_deadline_type IS NULL OR supplement_deadline_type IN ('calendar','working')) AND
    (on_supplement_timeout    IS NULL OR on_supplement_timeout    IN ('notify','auto_pass','auto_return'))
  );

-- ---------------------------------------------------------------------------
-- 3. 四个种子模板写 V0.4 定稿默认值（默认行为不变；后台可改）
--    依据 doc/prd-0.1.md 附录 D Q6/Q7 与 doc/templates.md §1.7
-- ---------------------------------------------------------------------------
UPDATE flow_template
   SET max_return_count         = 5,
       max_supplement_count     = 3,
       supplement_deadline_days = 3,
       supplement_deadline_type = 'working',
       on_supplement_timeout    = 'notify'
 WHERE code IN ('matter', 'fund', 'contract', 'seal');
