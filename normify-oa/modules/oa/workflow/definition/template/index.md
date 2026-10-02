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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.799Z"
fingerprint: 1ebf4aee4ac8648bfaec0ea0afbfecc1b3e2f5fecc0dff532be914d9e85be0f2
source:
  - path: "doc/data-model.md"
    line: 276
    end_line: 295
  - path: "doc/prd-0.1.md"
    line: 349
    end_line: 349
deps:
  - kind: reference
    to: oa.form.template
    label: {zh: "模板绑定表单与字段", en: "Binds form template & schema"}
---
