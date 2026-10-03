---
uid: 07fbb9b9
id: oa.form.template.schema.form-type
parent: oa.form.template.schema
state: planned
name: {zh: "四类单据模板", en: "Four Document Templates"}
description:
  zh: >
      四类单据（事项 matter / 资金 fund / 合同 contract / 印鉴证照 seal）的模板组合：表单模板 + 流程模板，靠组合区分而非分支；模板版本号与发布；已发起单据不受模板变更影响。
      
  en: >
      Template composition for the four document types (matter/fund/contract/seal): form template plus process template, distinguished by composition rather than branching; template versions and publishing; started documents are unaffected by later template changes.
      
revision: c22d447e6e63ccb0edfd9624026f21e8d1413077
updated_at: "2026-10-03T02:20:07.652Z"
fingerprint: a590145bc5717823c1c716f7cd29b05065b1aa8bf16dddb19dfc3b30d5207ee6
source:
  - path: "doc/forms.md"
    line: 395
    end_line: 402
  - path: "doc/prd-0.1.md"
    line: 266
    end_line: 279
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/templates"
    description:
      zh: >
          列出四类单据模板。
          
      en: >
          Lists the four document templates.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/templates/{form_type}"
    description:
      zh: >
          读取单据模板详情。
          
      en: >
          Reads a document template's detail.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/templates/{form_type}/versions"
    description:
      zh: >
          新建模板版本（字段变更先升版本）。
          
      en: >
          Creates a new template version (field changes bump the version first).
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/templates/{form_type}/versions/{version}/publish"
    description:
      zh: >
          发布模板版本，供新单据使用。
          
      en: >
          Publishes a template version for new documents.
          
deps:
  - kind: reference
    to: oa.workflow.definition
    from_api: "GET /api/v1/forms/templates/{form_type}"
    label: {zh: "表单模板与流程模板组合", en: "Form and process templates"}
---
