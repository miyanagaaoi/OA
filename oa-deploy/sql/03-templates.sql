-- =============================================================================
-- 03-templates.sql · 四类单据「流程模板 + 表单模板」初始化脚本
-- -----------------------------------------------------------------------------
-- 项目    ：集团OA审批系统（V0.4）
-- 数据库  ：MySQL 8.0（InnoDB / utf8mb4；JSON 列使用 MySQL 原生 JSON 类型）
-- 用途    ：为四类单据（matter / fund / contract / seal）各初始化 1 条
--           `flow_template`（version = 1、status = published、node_count = 7）
--           与 7 条 `flow_node`（主干 7 节点，4 × 7 = 28 行），并按 templates.md §2
--           写入完整可用的 `form_schema_json`（字段项 / 校验规则 / 联动 / 分区）。
--
-- 执行顺序：必须在 `01-schema.sql`（建表；列定义见 data-model.md §3.6 / §4.1 / §4.2）
--           之后执行。同目录（oa-deploy/sql/）发布顺序：
--             01-schema.sql → 02-dict-seed.sql → 03-templates.sql（本文件）
--
-- 依赖    ：① `flow_template`、`flow_node` 两表已建（唯一键
--              uk_flow_template(code, version)、uk_flow_node_seq(template_id, seq)，
--              与 01-schema.sql 的 DDL 一致）；
--           ② **数据字典必须先初始化**（02-dict-seed.sql）：本文件的 `form_schema_json`
--              只引用字典类型名（optionsSource.dictType），不插入字典行；字典缺失时
--              表单下拉为空，且 `rules[type=inDict]` 的服务端校验无法通过。
--              字典白名单见 doc/dict-seed.md §0.3；本文件实际引用
--              matter_category / contract_type / seal_type / cert_type /
--              payment_method / review_dept_other / return_status（7 个，均由 02 播种）；
--           ③ 组织与人员数据非必需——`approver_rule` 存的是解析规则码，人员解析在
--              流程发起时执行（enums.md §3「共同约束」第 1 条）。
--
-- 幂等    ：全部 INSERT 均为 `INSERT ... ON DUPLICATE KEY UPDATE`，可重复执行：
--           命中唯一键时只覆盖字段值，不产生重复行（模板恒为 4 行、节点恒为 28 行）。
--           注意：重复执行会把已发布的 v1 模板重置回本文件的口径（发布后改配置
--           必须升 version，见 templates.md §3.3 / §4.3）。
--
-- 生成依据（逐节核对；未臆造列名与取值）：
--   · doc/templates.md §0（定稿默认值 T-01…T-08）、§1.0（通用口径）、
--     §1.1–§1.4（四类单据 × 7 节点配置表）、§1.5（差异摘要）、
--     §1.6（flow_node 种子 JSON）、§2.1–§2.3（form_schema_json 结构契约）、
--     §2.4（事项单最小可用完整示例，本文件的事项单字段项与其逐键对齐）、
--     §2.5（服务端校验要求）、§3（版本与快照规则）
--   · doc/data-model.md §3.6（sys_dict_item）、§4.1（flow_template 列定义）、
--     §4.2（flow_node 列定义）
--   · doc/forms.md §1（通用约定 / 附件限制 / 金额规则）、§2–§5（四类单据字段清单）、
--     §6.1–§6.9（字段 code ↔ 字典类型绑定）、§7（四类字段对照）、§10（printLabel 对照）
--   · doc/enums.md §2（节点码）、§3（审批人解析规则码）、§10.2（单据类型）、
--     §11（14 种字段类型）、§12.1 / §12.2（附件允许 / 禁止格式）
--   · doc/dict-seed.md §0.3（字典类型白名单）、§1–§9（种子数据口径）
--
-- 节点列口径：`flow_template` / `flow_node` 列名严格照 DDL；`flow_node` 的 16 列顺序为
--   template_id, seq, node_code, name, node_type, approver_rule, approver_param,
--   decision_mode, pass_threshold, sign_policy, timeout_hours, timeout_cc_superior,
--   allow_add_sign, allow_jump, allow_route, skip_condition
--
-- 硬性口径（与 templates.md §1 逐行对齐）：
--   · 节点顺序：① dept_leader ② finance_review ③ branch_leader ④ subsidiary_gm
--               ⑤ group_leader ⑥ chairman ⑦ archive_register
--   · timeout_hours：② = 48，其余节点（①③④⑤⑥⑦）= 24（T-01；超时仅催办，
--                    不自动跳过、不自动升级）
--   · timeout_cc_superior：templates.md §1 未逐节点指明 → 全部取 DDL 默认语义 0
--                    （基线不抄送上级；test-cases.md §1.3 的超时用例另用模板变体置 1）
--   · allow_route：仅 ②⑤⑥ = 1，其余 = 0（T-02，仅集团层开启）
--   · allow_jump：全部 = 0（T-08，一期全部关闭，仅管理员显式开启）
--   · allow_add_sign：按 §1 表口径 ①–⑥ = 1、⑦ = 0（T-04 明确 ③④ 允许）
--   · sign_policy：⑤⑥ = required（强制签名）；⑦ = none（T-03 仅登记不审批）；
--                  其余 = optional
--   · ⑦：node_type = 'archive'、decision_mode = NULL、pass_threshold = NULL、
--        approver_rule = 'designated'、approver_param = {"role_code":"finance_clerk"}
--   · ② 跳过条件：仅事项单 = {"field":"involve_cost","op":"eq","value":false}；
--                  资金 / 合同 / 印鉴三类恒为「涉及」，skip_condition = NULL
--
-- SQL 约定：JSON 一律写成**单引号字符串字面量**，字面量内部不出现未转义的单引号
--           （本文全部 message 文案为中文，不含 ' 字符）；中文按 UTF-8 存储；
--           文件末尾给出执行后的自检 SQL。
-- =============================================================================

SET NAMES utf8mb4;

-- =============================================================================
-- 1. 流程模板（flow_template）：4 行 —— matter / fund / contract / seal，version = 1
--    form_schema_json 为 §2 契约的完整表单模板（四类各一份）。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1.1 事项审批单（code = matter，form_type = matter）· 9 个字段
--     依据：templates.md §2.4（最小可用完整示例）、forms.md §2
-- ---------------------------------------------------------------------------
INSERT INTO flow_template (code, name, form_type, version, status, node_count, form_schema_json, published_at)
VALUES (
  'matter', '事项审批单流程', 'matter', 1, 'published', 7,
  '{
  "form_type": "matter",
  "template_code": "matter",
  "schema_version": 1,
  "published_at": "2026-07-09T10:00:00Z",
  "sections": [
    {"id": "basic", "title": "基本信息", "printTitle": "事项基本信息", "collapsible": true, "fields": ["title", "category", "description"]},
    {"id": "cost", "title": "费用信息", "printTitle": "资金审批内容", "collapsible": true, "fields": ["involve_cost", "amount", "cost_bearer"]},
    {"id": "other", "title": "其他信息", "printTitle": "其他信息", "collapsible": true, "fields": ["expect_date", "cc_users", "attachments"]}
  ],
  "fields": [
    {"code": "title", "label": "事项标题", "printLabel": "事项标题", "printVisible": true, "type": "text", "required": true, "rules": [{"type": "maxLength", "value": 60, "message": "事项标题不能超过 60 个字符"}], "maxLength": 60, "placeholder": "请简要填写事项标题", "readonlyAfterSubmit": true},
    {"code": "category", "label": "事项类别", "printLabel": "事项分类", "printVisible": true, "type": "select", "required": true, "rules": [{"type": "inDict", "value": "matter_category", "message": "事项类别取值非法"}], "maxLength": 0, "optionsSource": {"kind": "dict", "dictType": "matter_category"}, "readonlyAfterSubmit": true},
    {"code": "description", "label": "事项描述", "printLabel": "事项描述", "printVisible": true, "type": "textarea", "required": true, "rules": [{"type": "minLength", "value": 10, "message": "事项描述至少 10 个字符"}, {"type": "maxLength", "value": 2000, "message": "事项描述不能超过 2000 个字符"}], "maxLength": 2000, "readonlyAfterSubmit": true},
    {"code": "involve_cost", "label": "是否涉及费用", "printLabel": "是否涉及费用", "printVisible": false, "type": "boolean", "required": true, "rules": [], "maxLength": 0, "defaultValue": false, "readonlyAfterSubmit": true},
    {"code": "amount", "label": "涉及金额", "printLabel": "涉及金额", "printVisible": true, "type": "amount", "required": false, "rules": [{"type": "conditionalRequired", "when": {"field": "involve_cost", "op": "eq", "value": true}, "message": "涉及费用时，涉及金额为必填"}, {"type": "amountRange", "min": "0.01", "max": "99999999999.99", "scale": 2, "message": "金额必须大于 0 且最多两位小数"}], "maxLength": 0, "linkage": {"visibleWhen": {"field": "involve_cost", "op": "eq", "value": true}, "requiredWhen": {"field": "involve_cost", "op": "eq", "value": true}, "clearWhen": {"field": "involve_cost", "op": "eq", "value": false}}, "readonlyAfterSubmit": true},
    {"code": "cost_bearer", "label": "费用承担主体", "printLabel": "费用承担主体", "printVisible": false, "type": "org", "required": false, "rules": [{"type": "conditionalRequired", "when": {"field": "involve_cost", "op": "eq", "value": true}, "message": "涉及费用时，费用承担主体为必填"}, {"type": "orgScope", "value": "initiator_company_subtree", "message": "费用承担主体限本公司及以下节点"}], "maxLength": 0, "defaultValue": "initiator_company", "linkage": {"visibleWhen": {"field": "involve_cost", "op": "eq", "value": true}, "requiredWhen": {"field": "involve_cost", "op": "eq", "value": true}, "clearWhen": {"field": "involve_cost", "op": "eq", "value": false}}, "readonlyAfterSubmit": true},
    {"code": "expect_date", "label": "期望完成日期", "printLabel": "期望完成日期", "printVisible": true, "type": "date", "required": false, "rules": [{"type": "dateNotBefore", "value": "today", "message": "期望完成日期不能早于今天"}], "maxLength": 0, "readonlyAfterSubmit": true},
    {"code": "cc_users", "label": "抄送人", "printLabel": "抄送", "printVisible": true, "type": "user", "required": false, "rules": [{"type": "pickerLimit", "max": 20, "message": "抄送人最多选择 20 人"}], "maxLength": 0, "readonlyAfterSubmit": true},
    {"code": "attachments", "label": "附件", "printLabel": "附送材料", "printVisible": true, "type": "files", "required": false, "rules": [{"type": "filePolicy", "maxSizeMb": 50, "maxCount": 20, "allowExt": ["pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "jpg", "jpeg", "png", "heic", "wps", "zip", "rar", "7z"], "denyExt": ["exe", "bat", "cmd", "js", "vbs", "ps1", "dll", "msi", "scr"], "message": "附件仅支持 pdf/doc/docx/xls/xlsx/ppt/pptx/jpg/jpeg/png/heic/wps/zip/rar/7z，单个文件不超过 50MB；heic 转 jpg 预览、wps 请下载查看"}], "maxLength": 0, "readonlyAfterSubmit": false}
  ]
}',
  '2026-07-09 10:00:00'
)
ON DUPLICATE KEY UPDATE
  name = VALUES(name), form_type = VALUES(form_type), status = VALUES(status),
  node_count = VALUES(node_count), form_schema_json = VALUES(form_schema_json),
  published_at = VALUES(published_at);

-- ---------------------------------------------------------------------------
-- 1.2 资金审批单（code = fund，form_type = fund）· 12 个字段
--     依据：forms.md §3；plan_category / payment_belong 为布尔 checkbox（非字典项，
--     选项写在字段项 options 中，见 dict-seed.md §6–§7；一期只存不用）
-- ---------------------------------------------------------------------------
INSERT INTO flow_template (code, name, form_type, version, status, node_count, form_schema_json, published_at)
VALUES (
  'fund', '资金审批单流程', 'fund', 1, 'published', 7,
  '{
  "form_type": "fund",
  "template_code": "fund",
  "schema_version": 1,
  "published_at": "2026-07-09T10:00:00Z",
  "sections": [
    {"id": "basic", "title": "基本信息", "printTitle": "资金基本信息", "collapsible": true, "fields": ["title", "category", "plan_category", "payment_belong"]},
    {"id": "pay", "title": "付款信息", "printTitle": "资金审批内容", "collapsible": true, "fields": ["amount", "payee", "payee_account", "pay_method", "pay_date", "contract_ref", "urgent"]},
    {"id": "other", "title": "其他信息", "printTitle": "附送材料", "collapsible": true, "fields": ["attachments"]}
  ],
  "fields": [
    {"code": "title", "label": "资金事由", "printLabel": "资金事由", "printVisible": true, "type": "text", "required": true, "rules": [{"type": "maxLength", "value": 60, "message": "资金事由不能超过 60 个字符"}], "maxLength": 60, "placeholder": "请简要填写资金事由", "readonlyAfterSubmit": true},
    {"code": "category", "label": "事项分类", "printLabel": "事项分类", "printVisible": true, "type": "select", "required": true, "rules": [{"type": "inDict", "value": "matter_category", "message": "事项分类取值非法"}], "maxLength": 0, "defaultValue": "economy", "optionsSource": {"kind": "dict", "dictType": "matter_category"}, "readonlyAfterSubmit": true},
    {"code": "plan_category", "label": "计划类别", "printLabel": "计划类别", "printVisible": true, "type": "boolean", "required": false, "rules": [], "maxLength": 0, "defaultValue": true, "options": [{"code": "in_plan", "label": "计划内", "enabled": true, "sortNo": 10}, {"code": "out_plan", "label": "计划外", "enabled": true, "sortNo": 20}], "readonlyAfterSubmit": true},
    {"code": "payment_belong", "label": "付款归属", "printLabel": "付款归属", "printVisible": true, "type": "boolean", "required": false, "rules": [], "maxLength": 0, "defaultValue": true, "options": [{"code": "current_month", "label": "本月度", "enabled": true, "sortNo": 10}, {"code": "current_year", "label": "本年度", "enabled": true, "sortNo": 20}, {"code": "prior_year", "label": "以前年度", "enabled": true, "sortNo": 30}], "readonlyAfterSubmit": true},
    {"code": "amount", "label": "申请金额", "printLabel": "申请金额", "printVisible": true, "type": "amount", "required": true, "rules": [{"type": "amountRange", "min": "0.01", "max": "99999999999.99", "scale": 2, "message": "金额必须大于 0 且最多两位小数"}], "maxLength": 0, "readonlyAfterSubmit": true},
    {"code": "payee", "label": "收款方名称", "printLabel": "收款方名称", "printVisible": true, "type": "text", "required": true, "rules": [{"type": "maxLength", "value": 100, "message": "收款方名称不能超过 100 个字符"}], "maxLength": 100, "readonlyAfterSubmit": true},
    {"code": "payee_account", "label": "收款账号", "printLabel": "收款账号", "printVisible": true, "type": "text", "required": true, "rules": [{"type": "maxLength", "value": 40, "message": "收款账号不能超过 40 个字符"}, {"type": "pattern", "value": "^[0-9A-Za-z-]+$", "message": "收款账号只能包含数字、字母与连字符"}], "maxLength": 40, "readonlyAfterSubmit": true},
    {"code": "pay_method", "label": "支付方式", "printLabel": "支付方式", "printVisible": true, "type": "select", "required": true, "rules": [{"type": "inDict", "value": "payment_method", "message": "支付方式取值非法"}], "maxLength": 0, "optionsSource": {"kind": "dict", "dictType": "payment_method"}, "readonlyAfterSubmit": true},
    {"code": "pay_date", "label": "计划支付日期", "printLabel": "计划支付日期", "printVisible": true, "type": "date", "required": true, "rules": [{"type": "dateNotBefore", "value": "today", "message": "计划支付日期不能早于今天"}], "maxLength": 0, "readonlyAfterSubmit": true},
    {"code": "contract_ref", "label": "关联合同单号", "printLabel": "关联合同单号", "printVisible": true, "type": "text", "required": false, "rules": [{"type": "maxLength", "value": 40, "message": "关联合同单号不能超过 40 个字符"}, {"type": "unique", "scope": "flow_instance.biz_no", "message": "关联合同单号必须是已存在且已通过的合同审批单号"}], "maxLength": 40, "readonlyAfterSubmit": true},
    {"code": "urgent", "label": "是否加急", "printLabel": "是否加急", "printVisible": true, "type": "boolean", "required": true, "rules": [], "maxLength": 0, "defaultValue": false, "readonlyAfterSubmit": true},
    {"code": "attachments", "label": "附件", "printLabel": "附送材料", "printVisible": true, "type": "files", "required": true, "rules": [{"type": "filePolicy", "maxSizeMb": 50, "maxCount": 20, "allowExt": ["pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "jpg", "jpeg", "png", "heic", "wps", "zip", "rar", "7z"], "denyExt": ["exe", "bat", "cmd", "js", "vbs", "ps1", "dll", "msi", "scr"], "message": "附件仅支持 pdf/doc/docx/xls/xlsx/ppt/pptx/jpg/jpeg/png/heic/wps/zip/rar/7z，单个文件不超过 50MB；heic 转 jpg 预览、wps 请下载查看"}], "maxLength": 0, "readonlyAfterSubmit": false}
  ]
}',
  '2026-07-09 10:00:00'
)
ON DUPLICATE KEY UPDATE
  name = VALUES(name), form_type = VALUES(form_type), status = VALUES(status),
  node_count = VALUES(node_count), form_schema_json = VALUES(form_schema_json),
  published_at = VALUES(published_at);

-- ---------------------------------------------------------------------------
-- 1.3 合同审批单（code = contract，form_type = contract）· 14 个字段
--     依据：forms.md §4；other_review_depts 的字典类型为 review_dept_other
--     （字段 code 与字典类型是两个命名空间，见 dict-seed.md §0.5）
-- ---------------------------------------------------------------------------
INSERT INTO flow_template (code, name, form_type, version, status, node_count, form_schema_json, published_at)
VALUES (
  'contract', '合同审批单流程', 'contract', 1, 'published', 7,
  '{
  "form_type": "contract",
  "template_code": "contract",
  "schema_version": 1,
  "published_at": "2026-07-09T10:00:00Z",
  "sections": [
    {"id": "basic", "title": "合同基本信息", "printTitle": "合同基本信息", "collapsible": true, "fields": ["title", "category", "contract_type", "contract_type_other", "counterparty", "counterparty_credit"]},
    {"id": "perform", "title": "金额与履约", "printTitle": "合同金额与履约期限", "collapsible": true, "fields": ["amount", "period_start", "period_end", "is_framework"]},
    {"id": "seal", "title": "用印与会审", "printTitle": "拟用印类型与其他会审部门", "collapsible": true, "fields": ["seal_type", "other_review_depts"]},
    {"id": "other", "title": "附件", "printTitle": "附送材料", "collapsible": true, "fields": ["attachments", "counterparty_docs"]}
  ],
  "fields": [
    {"code": "title", "label": "合同名称", "printLabel": "合同名称", "printVisible": true, "type": "text", "required": true, "rules": [{"type": "maxLength", "value": 80, "message": "合同名称不能超过 80 个字符"}], "maxLength": 80, "readonlyAfterSubmit": true},
    {"code": "category", "label": "事项类别", "printLabel": "事项分类", "printVisible": true, "type": "select", "required": true, "rules": [{"type": "inDict", "value": "matter_category", "message": "事项类别取值非法"}], "maxLength": 0, "defaultValue": "business", "optionsSource": {"kind": "dict", "dictType": "matter_category"}, "locked": true, "readonlyAfterSubmit": true},
    {"code": "contract_type", "label": "合同类型", "printLabel": "合同类型", "printVisible": true, "type": "select", "required": true, "rules": [{"type": "inDict", "value": "contract_type", "message": "合同类型取值非法"}], "maxLength": 0, "optionsSource": {"kind": "dict", "dictType": "contract_type"}, "readonlyAfterSubmit": true},
    {"code": "contract_type_other", "label": "其他类型说明", "printLabel": "其他类型说明", "printVisible": true, "type": "text", "required": false, "rules": [{"type": "maxLength", "value": 40, "message": "其他类型说明不能超过 40 个字符"}, {"type": "conditionalRequired", "when": {"field": "contract_type", "op": "eq", "value": "other"}, "message": "合同类型为其他时，其他类型说明为必填"}], "maxLength": 40, "linkage": {"visibleWhen": {"field": "contract_type", "op": "eq", "value": "other"}, "requiredWhen": {"field": "contract_type", "op": "eq", "value": "other"}, "clearWhen": {"field": "contract_type", "op": "ne", "value": "other"}}, "readonlyAfterSubmit": true},
    {"code": "counterparty", "label": "对方主体名称", "printLabel": "合同签订主体（乙方）", "printVisible": true, "type": "text", "required": true, "rules": [{"type": "maxLength", "value": 100, "message": "对方主体名称不能超过 100 个字符"}], "maxLength": 100, "readonlyAfterSubmit": true},
    {"code": "counterparty_credit", "label": "对方统一社会信用代码", "printLabel": "对方统一社会信用代码", "printVisible": true, "type": "text", "required": true, "rules": [{"type": "pattern", "value": "^[0-9A-Z]{18}$", "message": "统一社会信用代码须为 18 位数字或大写字母"}, {"type": "maxLength", "value": 18, "message": "对方统一社会信用代码不能超过 18 个字符"}], "maxLength": 18, "readonlyAfterSubmit": true},
    {"code": "amount", "label": "合同金额", "printLabel": "合同金额", "printVisible": true, "type": "amount", "required": true, "rules": [{"type": "amountRange", "min": "0.01", "max": "99999999999.99", "scale": 2, "message": "金额必须大于 0 且最多两位小数"}], "maxLength": 0, "readonlyAfterSubmit": true},
    {"code": "period_start", "label": "履约开始日期", "printLabel": "履约期限", "printVisible": true, "type": "date", "required": true, "rules": [], "maxLength": 0, "readonlyAfterSubmit": true},
    {"code": "period_end", "label": "履约结束日期", "printLabel": "履约期限", "printVisible": true, "type": "date", "required": true, "rules": [{"type": "dateNotBeforeField", "field": "period_start", "message": "履约结束日期不能早于履约开始日期"}], "maxLength": 0, "readonlyAfterSubmit": true},
    {"code": "is_framework", "label": "是否框架合同", "printLabel": "是否框架合同", "printVisible": true, "type": "boolean", "required": true, "rules": [], "maxLength": 0, "defaultValue": false, "readonlyAfterSubmit": true},
    {"code": "seal_type", "label": "拟用印类型", "printLabel": "拟用印类型", "printVisible": true, "type": "select", "required": true, "rules": [{"type": "inDict", "value": "seal_type", "message": "拟用印类型取值非法"}], "maxLength": 0, "defaultValue": "contract_seal", "optionsSource": {"kind": "dict", "dictType": "seal_type"}, "readonlyAfterSubmit": true},
    {"code": "other_review_depts", "label": "其他会审部门", "printLabel": "其他会审部门", "printVisible": true, "type": "multiselect", "required": false, "rules": [{"type": "inDict", "value": "review_dept_other", "message": "其他会审部门取值非法"}, {"type": "pickerLimit", "max": 10, "message": "其他会审部门最多选择 10 项"}], "maxLength": 0, "optionsSource": {"kind": "dict", "dictType": "review_dept_other"}, "readonlyAfterSubmit": true},
    {"code": "attachments", "label": "合同文本附件", "printLabel": "附送材料", "printVisible": true, "type": "files", "required": true, "rules": [{"type": "filePolicy", "maxSizeMb": 50, "maxCount": 20, "allowExt": ["pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "jpg", "jpeg", "png", "heic", "wps", "zip", "rar", "7z"], "denyExt": ["exe", "bat", "cmd", "js", "vbs", "ps1", "dll", "msi", "scr"], "message": "附件仅支持 pdf/doc/docx/xls/xlsx/ppt/pptx/jpg/jpeg/png/heic/wps/zip/rar/7z，单个文件不超过 50MB；heic 转 jpg 预览、wps 请下载查看"}], "maxLength": 0, "readonlyAfterSubmit": false},
    {"code": "counterparty_docs", "label": "对方资质附件", "printLabel": "对方资质材料", "printVisible": true, "type": "files", "required": false, "rules": [{"type": "filePolicy", "maxSizeMb": 50, "maxCount": 20, "allowExt": ["pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "jpg", "jpeg", "png", "heic", "wps", "zip", "rar", "7z"], "denyExt": ["exe", "bat", "cmd", "js", "vbs", "ps1", "dll", "msi", "scr"], "message": "附件仅支持 pdf/doc/docx/xls/xlsx/ppt/pptx/jpg/jpeg/png/heic/wps/zip/rar/7z，单个文件不超过 50MB；heic 转 jpg 预览、wps 请下载查看"}], "maxLength": 0, "readonlyAfterSubmit": false}
  ]
}',
  '2026-07-09 10:00:00'
)
ON DUPLICATE KEY UPDATE
  name = VALUES(name), form_type = VALUES(form_type), status = VALUES(status),
  node_count = VALUES(node_count), form_schema_json = VALUES(form_schema_json),
  published_at = VALUES(published_at);

-- ---------------------------------------------------------------------------
-- 1.4 印鉴证照审批单（code = seal，form_type = seal）· 12 个字段
--     依据：forms.md §5；return_status / return_date 是三态读写模型的唯一例外
--     （审批中仅发起人与节点⑦可改）→ readonlyAfterSubmit = false
-- ---------------------------------------------------------------------------
INSERT INTO flow_template (code, name, form_type, version, status, node_count, form_schema_json, published_at)
VALUES (
  'seal', '印鉴证照审批单流程', 'seal', 1, 'published', 7,
  '{
  "form_type": "seal",
  "template_code": "seal",
  "schema_version": 1,
  "published_at": "2026-07-09T10:00:00Z",
  "sections": [
    {"id": "basic", "title": "基本信息", "printTitle": "用印基本信息", "collapsible": true, "fields": ["title", "category", "seal_type", "cert_name", "purpose"]},
    {"id": "usage", "title": "使用期限与份数", "printTitle": "使用期限与用印份数", "collapsible": true, "fields": ["usage_start", "usage_end", "seal_count", "is_external"]},
    {"id": "return", "title": "归还登记", "printTitle": "证件归还登记", "collapsible": true, "fields": ["return_status", "return_date"]},
    {"id": "other", "title": "附件", "printTitle": "附送材料", "collapsible": true, "fields": ["attachments"]}
  ],
  "fields": [
    {"code": "title", "label": "用印/借用事由", "printLabel": "用印/借用事由", "printVisible": true, "type": "text", "required": true, "rules": [{"type": "maxLength", "value": 60, "message": "用印/借用事由不能超过 60 个字符"}], "maxLength": 60, "readonlyAfterSubmit": true},
    {"code": "category", "label": "事项类别", "printLabel": "事项分类", "printVisible": true, "type": "select", "required": true, "rules": [{"type": "inDict", "value": "matter_category", "message": "事项类别取值非法"}], "maxLength": 0, "defaultValue": "admin", "optionsSource": {"kind": "dict", "dictType": "matter_category"}, "locked": true, "readonlyAfterSubmit": true},
    {"code": "seal_type", "label": "用印类型", "printLabel": "用印类型", "printVisible": true, "type": "select", "required": true, "rules": [{"type": "inDict", "value": "seal_type", "message": "用印类型取值非法"}], "maxLength": 0, "optionsSource": {"kind": "dict", "dictType": "seal_type"}, "readonlyAfterSubmit": true},
    {"code": "cert_name", "label": "证照类型", "printLabel": "证照类型", "printVisible": true, "type": "select", "required": false, "rules": [{"type": "inDict", "value": "cert_type", "message": "证照类型取值非法"}, {"type": "conditionalRequired", "when": {"field": "seal_type", "op": "eq", "value": "cert_borrow"}, "message": "用印类型为证照借用时，证照类型为必填"}], "maxLength": 0, "optionsSource": {"kind": "dict", "dictType": "cert_type"}, "linkage": {"visibleWhen": {"field": "seal_type", "op": "eq", "value": "cert_borrow"}, "requiredWhen": {"field": "seal_type", "op": "eq", "value": "cert_borrow"}, "clearWhen": {"field": "seal_type", "op": "ne", "value": "cert_borrow"}}, "readonlyAfterSubmit": true},
    {"code": "purpose", "label": "用途说明", "printLabel": "用途说明", "printVisible": true, "type": "textarea", "required": true, "rules": [{"type": "minLength", "value": 5, "message": "用途说明至少 5 个字符"}, {"type": "conditionalMinLength", "when": {"field": "is_external", "op": "eq", "value": true}, "min": 20, "message": "对外提供时用途说明不得少于 20 个字符"}, {"type": "maxLength", "value": 500, "message": "用途说明不能超过 500 个字符"}], "maxLength": 500, "readonlyAfterSubmit": true},
    {"code": "usage_start", "label": "使用开始日期", "printLabel": "使用开始日期", "printVisible": true, "type": "date", "required": true, "rules": [{"type": "dateNotBefore", "value": "today", "message": "使用开始日期不能早于今天"}], "maxLength": 0, "readonlyAfterSubmit": true},
    {"code": "usage_end", "label": "使用结束日期", "printLabel": "使用结束日期", "printVisible": true, "type": "date", "required": true, "rules": [{"type": "dateNotBeforeField", "field": "usage_start", "message": "使用结束日期不能早于使用开始日期"}], "maxLength": 0, "readonlyAfterSubmit": true},
    {"code": "seal_count", "label": "用印份数", "printLabel": "用印份数", "printVisible": true, "type": "number", "required": false, "rules": [{"type": "numberRange", "min": 1, "max": 999, "integer": true, "message": "用印份数须为 1 到 999 的整数"}, {"type": "conditionalRequired", "when": {"field": "seal_type", "op": "ne", "value": "cert_borrow"}, "message": "非证照借用时，用印份数为必填"}], "maxLength": 0, "defaultValue": 1, "linkage": {"visibleWhen": {"field": "seal_type", "op": "ne", "value": "cert_borrow"}, "requiredWhen": {"field": "seal_type", "op": "ne", "value": "cert_borrow"}, "clearWhen": {"field": "seal_type", "op": "eq", "value": "cert_borrow"}}, "readonlyAfterSubmit": true},
    {"code": "is_external", "label": "是否对外提供", "printLabel": "是否对外提供", "printVisible": true, "type": "boolean", "required": true, "rules": [], "maxLength": 0, "defaultValue": false, "readonlyAfterSubmit": true},
    {"code": "return_status", "label": "归还状态", "printLabel": "证件归还状态", "printVisible": true, "type": "select", "required": true, "rules": [{"type": "inDict", "value": "return_status", "message": "归还状态取值非法"}], "maxLength": 0, "defaultValue": "pending", "optionsSource": {"kind": "dict", "dictType": "return_status"}, "readonlyAfterSubmit": false},
    {"code": "return_date", "label": "归还日期", "printLabel": "归还日期", "printVisible": true, "type": "date", "required": false, "rules": [{"type": "conditionalRequired", "when": {"field": "return_status", "op": "eq", "value": "returned"}, "message": "归还状态为已归还时，归还日期为必填"}], "maxLength": 0, "linkage": {"visibleWhen": {"field": "return_status", "op": "eq", "value": "returned"}, "requiredWhen": {"field": "return_status", "op": "eq", "value": "returned"}, "clearWhen": {"field": "return_status", "op": "ne", "value": "returned"}}, "readonlyAfterSubmit": false},
    {"code": "attachments", "label": "附件", "printLabel": "附送材料", "printVisible": true, "type": "files", "required": false, "rules": [{"type": "filePolicy", "maxSizeMb": 50, "maxCount": 20, "allowExt": ["pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "jpg", "jpeg", "png", "heic", "wps", "zip", "rar", "7z"], "denyExt": ["exe", "bat", "cmd", "js", "vbs", "ps1", "dll", "msi", "scr"], "message": "附件仅支持 pdf/doc/docx/xls/xlsx/ppt/pptx/jpg/jpeg/png/heic/wps/zip/rar/7z，单个文件不超过 50MB；heic 转 jpg 预览、wps 请下载查看"}], "maxLength": 0, "readonlyAfterSubmit": false}
  ]
}',
  '2026-07-09 10:00:00'
)
ON DUPLICATE KEY UPDATE
  name = VALUES(name), form_type = VALUES(form_type), status = VALUES(status),
  node_count = VALUES(node_count), form_schema_json = VALUES(form_schema_json),
  published_at = VALUES(published_at);

-- =============================================================================
-- 2. 流程节点（flow_node）：4 个模板 × 7 节点 = 28 行
--    列顺序严格照 data-model.md §4.2；template_id 用子查询取对应模板 id。
--    共同口径：node_type ①–⑥ = approve、⑦ = archive；decision_mode ①–⑥ = any、
--    ⑦ = NULL；pass_threshold 全为 NULL（缺省＝过半，T-07）；
--    timeout_cc_superior 全为 0；allow_jump 全为 0；allow_route 仅 ②⑤⑥ = 1。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 2.1 事项审批单（matter）· 唯一允许 ② 被跳过的模板
-- ---------------------------------------------------------------------------
INSERT INTO flow_node (template_id, seq, node_code, name, node_type, approver_rule, approver_param, decision_mode, pass_threshold, sign_policy, timeout_hours, timeout_cc_superior, allow_add_sign, allow_jump, allow_route, skip_condition)
VALUES
((SELECT id FROM flow_template WHERE code='matter' AND version=1), 1, 'dept_leader', '直属部门负责人', 'approve', 'dept_leader_upward', NULL, 'any', NULL, 'optional', 24, 0, 1, 0, 0, NULL),
((SELECT id FROM flow_template WHERE code='matter' AND version=1), 2, 'finance_review', '财务部复核', 'approve', 'finance_owner', NULL, 'any', NULL, 'optional', 48, 0, 1, 0, 1, '{"field":"involve_cost","op":"eq","value":false}'),
((SELECT id FROM flow_template WHERE code='matter' AND version=1), 3, 'branch_leader', '分公司分管领导', 'approve', 'branch_leader', NULL, 'any', NULL, 'optional', 24, 0, 1, 0, 0, NULL),
((SELECT id FROM flow_template WHERE code='matter' AND version=1), 4, 'subsidiary_gm', '子公司总经理', 'approve', 'subsidiary_gm', NULL, 'any', NULL, 'optional', 24, 0, 1, 0, 0, NULL),
((SELECT id FROM flow_template WHERE code='matter' AND version=1), 5, 'group_leader', '集团分管领导', 'approve', 'group_leader', NULL, 'any', NULL, 'required', 24, 0, 1, 0, 1, NULL),
((SELECT id FROM flow_template WHERE code='matter' AND version=1), 6, 'chairman', '集团董事长', 'approve', 'chairman', NULL, 'any', NULL, 'required', 24, 0, 1, 0, 1, NULL),
((SELECT id FROM flow_template WHERE code='matter' AND version=1), 7, 'archive_register', '归档登记', 'archive', 'designated', '{"role_code":"finance_clerk"}', NULL, NULL, 'none', 24, 0, 0, 0, 0, NULL)
ON DUPLICATE KEY UPDATE
  node_code = VALUES(node_code), name = VALUES(name), node_type = VALUES(node_type),
  approver_rule = VALUES(approver_rule), approver_param = VALUES(approver_param),
  decision_mode = VALUES(decision_mode), pass_threshold = VALUES(pass_threshold),
  sign_policy = VALUES(sign_policy), timeout_hours = VALUES(timeout_hours),
  timeout_cc_superior = VALUES(timeout_cc_superior), allow_add_sign = VALUES(allow_add_sign),
  allow_jump = VALUES(allow_jump), allow_route = VALUES(allow_route),
  skip_condition = VALUES(skip_condition);

-- ---------------------------------------------------------------------------
-- 2.2 资金审批单（fund）· ② 恒为「涉及」，无 skip_condition
-- ---------------------------------------------------------------------------
INSERT INTO flow_node (template_id, seq, node_code, name, node_type, approver_rule, approver_param, decision_mode, pass_threshold, sign_policy, timeout_hours, timeout_cc_superior, allow_add_sign, allow_jump, allow_route, skip_condition)
VALUES
((SELECT id FROM flow_template WHERE code='fund' AND version=1), 1, 'dept_leader', '直属部门负责人', 'approve', 'dept_leader_upward', NULL, 'any', NULL, 'optional', 24, 0, 1, 0, 0, NULL),
((SELECT id FROM flow_template WHERE code='fund' AND version=1), 2, 'finance_review', '财务部复核', 'approve', 'finance_owner', NULL, 'any', NULL, 'optional', 48, 0, 1, 0, 1, NULL),
((SELECT id FROM flow_template WHERE code='fund' AND version=1), 3, 'branch_leader', '分公司分管领导', 'approve', 'branch_leader', NULL, 'any', NULL, 'optional', 24, 0, 1, 0, 0, NULL),
((SELECT id FROM flow_template WHERE code='fund' AND version=1), 4, 'subsidiary_gm', '子公司总经理', 'approve', 'subsidiary_gm', NULL, 'any', NULL, 'optional', 24, 0, 1, 0, 0, NULL),
((SELECT id FROM flow_template WHERE code='fund' AND version=1), 5, 'group_leader', '集团分管领导', 'approve', 'group_leader', NULL, 'any', NULL, 'required', 24, 0, 1, 0, 1, NULL),
((SELECT id FROM flow_template WHERE code='fund' AND version=1), 6, 'chairman', '集团董事长', 'approve', 'chairman', NULL, 'any', NULL, 'required', 24, 0, 1, 0, 1, NULL),
((SELECT id FROM flow_template WHERE code='fund' AND version=1), 7, 'archive_register', '归档登记', 'archive', 'designated', '{"role_code":"finance_clerk"}', NULL, NULL, 'none', 24, 0, 0, 0, 0, NULL)
ON DUPLICATE KEY UPDATE
  node_code = VALUES(node_code), name = VALUES(name), node_type = VALUES(node_type),
  approver_rule = VALUES(approver_rule), approver_param = VALUES(approver_param),
  decision_mode = VALUES(decision_mode), pass_threshold = VALUES(pass_threshold),
  sign_policy = VALUES(sign_policy), timeout_hours = VALUES(timeout_hours),
  timeout_cc_superior = VALUES(timeout_cc_superior), allow_add_sign = VALUES(allow_add_sign),
  allow_jump = VALUES(allow_jump), allow_route = VALUES(allow_route),
  skip_condition = VALUES(skip_condition);

-- ---------------------------------------------------------------------------
-- 2.3 合同审批单（contract）· ② 恒为「涉及」，无 skip_condition
-- ---------------------------------------------------------------------------
INSERT INTO flow_node (template_id, seq, node_code, name, node_type, approver_rule, approver_param, decision_mode, pass_threshold, sign_policy, timeout_hours, timeout_cc_superior, allow_add_sign, allow_jump, allow_route, skip_condition)
VALUES
((SELECT id FROM flow_template WHERE code='contract' AND version=1), 1, 'dept_leader', '直属部门负责人', 'approve', 'dept_leader_upward', NULL, 'any', NULL, 'optional', 24, 0, 1, 0, 0, NULL),
((SELECT id FROM flow_template WHERE code='contract' AND version=1), 2, 'finance_review', '财务部复核', 'approve', 'finance_owner', NULL, 'any', NULL, 'optional', 48, 0, 1, 0, 1, NULL),
((SELECT id FROM flow_template WHERE code='contract' AND version=1), 3, 'branch_leader', '分公司分管领导', 'approve', 'branch_leader', NULL, 'any', NULL, 'optional', 24, 0, 1, 0, 0, NULL),
((SELECT id FROM flow_template WHERE code='contract' AND version=1), 4, 'subsidiary_gm', '子公司总经理', 'approve', 'subsidiary_gm', NULL, 'any', NULL, 'optional', 24, 0, 1, 0, 0, NULL),
((SELECT id FROM flow_template WHERE code='contract' AND version=1), 5, 'group_leader', '集团分管领导', 'approve', 'group_leader', NULL, 'any', NULL, 'required', 24, 0, 1, 0, 1, NULL),
((SELECT id FROM flow_template WHERE code='contract' AND version=1), 6, 'chairman', '集团董事长', 'approve', 'chairman', NULL, 'any', NULL, 'required', 24, 0, 1, 0, 1, NULL),
((SELECT id FROM flow_template WHERE code='contract' AND version=1), 7, 'archive_register', '归档登记', 'archive', 'designated', '{"role_code":"finance_clerk"}', NULL, NULL, 'none', 24, 0, 0, 0, 0, NULL)
ON DUPLICATE KEY UPDATE
  node_code = VALUES(node_code), name = VALUES(name), node_type = VALUES(node_type),
  approver_rule = VALUES(approver_rule), approver_param = VALUES(approver_param),
  decision_mode = VALUES(decision_mode), pass_threshold = VALUES(pass_threshold),
  sign_policy = VALUES(sign_policy), timeout_hours = VALUES(timeout_hours),
  timeout_cc_superior = VALUES(timeout_cc_superior), allow_add_sign = VALUES(allow_add_sign),
  allow_jump = VALUES(allow_jump), allow_route = VALUES(allow_route),
  skip_condition = VALUES(skip_condition);

-- ---------------------------------------------------------------------------
-- 2.4 印鉴证照审批单（seal）· ② 恒为「涉及」，无 skip_condition
--     ⑦ 除归档登记外还承担归还状态登记（return_status / return_date 三态例外）
-- ---------------------------------------------------------------------------
INSERT INTO flow_node (template_id, seq, node_code, name, node_type, approver_rule, approver_param, decision_mode, pass_threshold, sign_policy, timeout_hours, timeout_cc_superior, allow_add_sign, allow_jump, allow_route, skip_condition)
VALUES
((SELECT id FROM flow_template WHERE code='seal' AND version=1), 1, 'dept_leader', '直属部门负责人', 'approve', 'dept_leader_upward', NULL, 'any', NULL, 'optional', 24, 0, 1, 0, 0, NULL),
((SELECT id FROM flow_template WHERE code='seal' AND version=1), 2, 'finance_review', '财务部复核', 'approve', 'finance_owner', NULL, 'any', NULL, 'optional', 48, 0, 1, 0, 1, NULL),
((SELECT id FROM flow_template WHERE code='seal' AND version=1), 3, 'branch_leader', '分公司分管领导', 'approve', 'branch_leader', NULL, 'any', NULL, 'optional', 24, 0, 1, 0, 0, NULL),
((SELECT id FROM flow_template WHERE code='seal' AND version=1), 4, 'subsidiary_gm', '子公司总经理', 'approve', 'subsidiary_gm', NULL, 'any', NULL, 'optional', 24, 0, 1, 0, 0, NULL),
((SELECT id FROM flow_template WHERE code='seal' AND version=1), 5, 'group_leader', '集团分管领导', 'approve', 'group_leader', NULL, 'any', NULL, 'required', 24, 0, 1, 0, 1, NULL),
((SELECT id FROM flow_template WHERE code='seal' AND version=1), 6, 'chairman', '集团董事长', 'approve', 'chairman', NULL, 'any', NULL, 'required', 24, 0, 1, 0, 1, NULL),
((SELECT id FROM flow_template WHERE code='seal' AND version=1), 7, 'archive_register', '归档登记', 'archive', 'designated', '{"role_code":"finance_clerk"}', NULL, NULL, 'none', 24, 0, 0, 0, 0, NULL)
ON DUPLICATE KEY UPDATE
  node_code = VALUES(node_code), name = VALUES(name), node_type = VALUES(node_type),
  approver_rule = VALUES(approver_rule), approver_param = VALUES(approver_param),
  decision_mode = VALUES(decision_mode), pass_threshold = VALUES(pass_threshold),
  sign_policy = VALUES(sign_policy), timeout_hours = VALUES(timeout_hours),
  timeout_cc_superior = VALUES(timeout_cc_superior), allow_add_sign = VALUES(allow_add_sign),
  allow_jump = VALUES(allow_jump), allow_route = VALUES(allow_route),
  skip_condition = VALUES(skip_condition);

-- =============================================================================
-- 3. 执行后自检 SQL（只读，不修改数据）
-- =============================================================================
-- 3.1 模板：应返回 4 行，node_count 全为 7，schema_valid 全为 1
SELECT code, version, status, node_count, JSON_VALID(form_schema_json) AS schema_valid,
       JSON_UNQUOTE(JSON_EXTRACT(form_schema_json, '$.schema_version')) AS schema_version,
       published_at
FROM flow_template
ORDER BY code;

-- 3.2 节点：每个模板应返回 7（4 × 7 = 28 行）
SELECT t.code, t.version, COUNT(*) AS node_rows
FROM flow_node n
JOIN flow_template t ON t.id = n.template_id
GROUP BY t.code, t.version
ORDER BY t.code;

-- 3.3 节点口径抽查：超时（仅 ② = 48）、流转开关（仅 ②⑤⑥ = 1）、自由跳转（全 0）
SELECT t.code, n.seq, n.node_code, n.timeout_hours, n.allow_route, n.allow_jump, n.sign_policy
FROM flow_node n
JOIN flow_template t ON t.id = n.template_id
ORDER BY t.code, n.seq;

-- 3.4 可跳过节点校验：应仅事项单出现 1 行，其余三类模板 0 行
SELECT t.code, COUNT(*) AS skippable_nodes
FROM flow_node n
JOIN flow_template t ON t.id = n.template_id
WHERE n.skip_condition IS NOT NULL
GROUP BY t.code
ORDER BY t.code;

-- 3.5 登记节点校验：7 行，decision_mode / pass_threshold 均为 NULL
SELECT t.code, n.seq, n.node_code, n.node_type, n.decision_mode, n.pass_threshold, n.approver_param
FROM flow_node n
JOIN flow_template t ON t.id = n.template_id
WHERE n.node_type = 'archive'
ORDER BY t.code, n.seq;
