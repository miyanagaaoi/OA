---
uid: 43c226f2
id: oa.admin.flow.form
parent: oa.admin.flow
name: {zh: "表单模板配置", en: "Form Template Config"}
description:
  zh: >
      维护各表单类型的 form_schema_json，校验必填字段，并检查每个选项列表仍能命中共享数据字典。
      
  en: >
      Maintains the form_schema_json behind each form type, validating required fields and checking that every option list still resolves against the shared data dictionary.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.187Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/form-templates/{form_type}"
    description:
      zh: >
          查询表单模板的字段定义。
          
      en: >
          Read the field definition of a form template.
          
  - protocol: http
    method: PUT
    path: "/api/v1/admin/form-templates/{form_type}"
    description:
      zh: >
          保存驱动渲染的字段定义 JSON。
          
      en: >
          Save the field schema that drives form rendering.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/form-templates/{form_type}/validate"
    description:
      zh: >
          校验必填字段与字典引用。
          
      en: >
          Validate required fields and dictionary references.
          
deps:
  - kind: call
    to: oa.form.template
    from_api: "PUT /api/v1/admin/form-templates/{form_type}"
    label: {zh: "表单字段定义", en: "Field schema for forms"}
  - kind: reference
    to: oa.admin.dict
    label: {zh: "字段选项取自数据字典", en: "Options from dictionary"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-002`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE form_data`（§4. 流程定义）
