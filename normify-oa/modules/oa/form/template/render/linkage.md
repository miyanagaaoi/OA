---
uid: 0f3933ac
id: oa.form.template.render.linkage
parent: oa.form.template.render
name: {zh: "字段联动求值", en: "Field Linkage Evaluation"}
description:
  zh: >
      联动规则求值：字段的显示、必填与取值依赖其他字段，如 involve_cost → amount/cost_bearer、contract_type → contract_type_other、seal_type → cert_name/seal_count、return_status → return_date、is_framework → period_end。
      
  en: >
      Evaluates linkage rules where visibility, requiredness and value depend on other fields, e.g. involve_cost → amount/cost_bearer, contract_type → contract_type_other, seal_type → cert_name/seal_count, return_status → return_date, is_framework → period_end.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.042Z"
fingerprint: 10c6effd215627f1c844a3acecef0995e0cae341763861c18553845cb71394d6
source:
  - path: "doc/forms.md"
  - path: "doc/forms.md"
  - path: "doc/forms.md"
  - path: "doc/forms.md"
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/render/{form_type}/linkage"
    description:
      zh: >
          求值联动结果（显示/必填/可写/取值）。
          
      en: >
          Evaluates linkage results (visibility, requiredness, writability, derived value).
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/templates/{form_type}/schema/linkages"
    description:
      zh: >
          读取模板声明的联动规则。
          
      en: >
          Reads the linkage rules declared by a template.
          
---

## 证据锚点
- `doc/forms.md` → `## 11. 表单模板实现要求`（§11. 表单模板实现要求）
