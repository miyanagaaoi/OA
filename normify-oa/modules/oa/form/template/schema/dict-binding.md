---
uid: 08adc0c1
id: oa.form.template.schema.dict-binding
parent: oa.form.template.schema
state: planned
name: {zh: "字典下拉绑定", en: "Dictionary Binding"}
description:
  zh: >
      select / multiselect / checkbox 类字段与数据字典的绑定：选项取自 `sys_dict_item`，新增选项不需发版；校验与打印按绑定字典解析取值名称。
      
  en: >
      Binds select / multiselect / checkbox fields to data dictionaries: options come from `sys_dict_item` and new options need no release; validation and printing resolve option names through the bound dictionary.
      
revision: 44fc7aba1c7e884ffa3553faf31bfce974b388a9
updated_at: "2026-10-03T04:12:20.720Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/templates/{form_type}/schema/fields/{field_id}/options"
    description:
      zh: >
          读取某字段的字典下拉选项。
          
      en: >
          Reads a field's dictionary options.
          
  - protocol: http
    method: PUT
    path: "/api/v1/forms/templates/{form_type}/schema/fields/{field_id}/dict"
    description:
      zh: >
          绑定或切换字段使用的字典键。
          
      en: >
          Binds or switches the dictionary key used by a field.
          
deps:
  - kind: call
    to: oa.form.dict
    from_api: "GET /api/v1/forms/templates/{form_type}/schema/fields/{field_id}/options"
    label: {zh: "下拉选项来源", en: "Option source"}
---

## 证据锚点
- `doc/forms.md` → `## 6. 数据字典与布尔字段取值（6.7 / 6.8 不是字典）`（§6. 数据字典与布尔字段取值）
