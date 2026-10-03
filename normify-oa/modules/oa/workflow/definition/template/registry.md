---
uid: 00951d3a
id: oa.workflow.definition.template.registry
parent: oa.workflow.definition.template
state: planned
name: {zh: "模板元数据与表单绑定", en: "Template Metadata & Form Binding"}
description:
  zh: >
      依据 flow_template 的 code / form_type 定位单据类型（matter/fund/contract/seal），维护模板名称、节点数与 form_schema_json 表单字段定义，驱动四类单据的表单渲染与字段字典（见 doc/forms.md）。
      
  en: >
      Resolves the document type (matter/fund/contract/seal) from flow_template.code/form_type and maintains the template name, node_count and the form_schema_json field definitions that drive form rendering and the field dictionary (see doc/forms.md).
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.311Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/data-model.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: mysql
    path: "flow_template"
    description:
      zh: >
          流程模板表：元数据、表单定义与节点数。
          
      en: >
          Flow template table: metadata, form definition and node count.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow-templates"
    description:
      zh: >
          按单据类型/编码查询模板列表与当前版本。
          
      en: >
          Lists templates and their current version by document type or code.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow-templates/{template_id}"
    description:
      zh: >
          读取模板元数据与表单字段定义。
          
      en: >
          Reads template metadata and the form field definition.
          
  - protocol: http
    method: POST
    path: "/api/v1/flow-templates"
    description:
      zh: >
          新建模板草稿（v1），绑定表单模板与单据类型。
          
      en: >
          Creates a draft template (v1) bound to a form template and document type.
          
deps:
  - kind: call
    to: oa.form.template
    from_api: "POST /api/v1/flow-templates"
    label: {zh: "拉取表单字段定义", en: "Fetch form fields into schema"}
---

## 证据锚点
- `doc/data-model.md` → `CREATE TABLE flow_template`（§4. 流程定义）
- `doc/prd-0.1.md` → `REQ-FLOW-006`（§6.4 流程引擎核心能力）
