---
uid: 07fbb9b9
id: oa.form.template.schema.form-type
parent: oa.form.template.schema
name: {zh: "四类单据模板", en: "Four Document Templates"}
description:
  zh: >
      四类单据（事项 matter / 资金 fund / 合同 contract / 印鉴证照 seal）的模板组合：表单模板 + 流程模板，靠组合区分而非分支；模板版本号与发布；已发起单据不受模板变更影响。
      
  en: >
      Template composition for the four document types (matter/fund/contract/seal): form template plus process template, distinguished by composition rather than branching; template versions and publishing; started documents are unaffected by later template changes.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.461Z"
fingerprint: 2abb03285e16884914f60eb0591090d90ed9568386362e96d42d3d5d3f7fc573
source:
  - path: "doc/forms.md"
  - path: "doc/prd-0.1.md"
  - path: "oa-server/src/main/java/com/oa/form/template/schema/FormSchema.java"
  - path: "oa-server/src/main/java/com/oa/form/template/schema/FormSchemaService.java"
  - path: "oa-server/src/main/java/com/oa/form/document/FormRuleRegistry.java"
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

## 证据锚点
- `doc/forms.md` → `## 11. 表单模板实现要求`（§11. 表单模板实现要求）
- `doc/prd-0.1.md` → `### 6.2 四类审批单与事项类别的关系`（§6.2 四类审批单与事项类别的关系）
