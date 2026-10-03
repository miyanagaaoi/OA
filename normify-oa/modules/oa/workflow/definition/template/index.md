---
uid: 002acac5
id: oa.workflow.definition.template
parent: oa.workflow.definition
state: planned
name: {zh: "流程模板与版本", en: "Templates & Versions"}
description:
  zh: >
      流程模板的元数据与版本累积：按 code 绑定单据类型与表单模板（form_schema_json 驱动渲染），模板变更生成新版本号且不覆盖历史；已发起实例按发起时锁定的版本与审批人快照执行，模板停用不影响在途实例（REQ-FLOW-006）。
      
  en: >
      Template metadata and version accumulation: each code binds a document type and form template (form_schema_json drives rendering); changes create new versions without overwriting history. In-flight instances keep the version and approver snapshot captured at submission, and disabling a template never affects them (REQ-FLOW-006).
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.598Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/data-model.md"
  - path: "doc/prd-0.1.md"
deps:
  - kind: reference
    to: oa.form.template
    label: {zh: "模板绑定表单与字段", en: "Binds form template & schema"}
---

## 证据锚点
- `doc/data-model.md` → `CREATE TABLE flow_template`（§4. 流程定义）
- `doc/prd-0.1.md` → `REQ-FLOW-006`（§6.4 流程引擎核心能力）
