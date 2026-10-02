---
uid: 43c226f2
id: oa.admin.flow.form
parent: oa.admin.flow
state: planned
name: {zh: "表单模板配置", en: "Form Template Config"}
description:
  zh: >
      维护各表单类型的 form_schema_json，校验必填字段，并检查每个选项列表仍能命中共享数据字典。
      
  en: >
      Maintains the form_schema_json behind each form type, validating required fields and checking that every option list still resolves against the shared data dictionary.
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.621Z"
fingerprint: 4421092e639ef98e9cd4aac53b5975054268b604256d140e3544a8a3c2ba5551
source:
  - path: "doc/prd-0.1.md"
    line: 436
    end_line: 436
  - path: "doc/data-model.md"
    line: 286
    end_line: 286
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
