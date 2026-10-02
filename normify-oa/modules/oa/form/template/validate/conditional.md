---
uid: 137904d4
id: oa.form.template.validate.conditional
parent: oa.form.template.validate
state: planned
name: {zh: "条件必填校验", en: "Conditional Required Validation"}
description:
  zh: >
      依赖字段满足条件时的非空校验：involve_cost=是 → amount/cost_bearer、contract_type=其他 → contract_type_other、seal_type=证照借用 → cert_name、return_status=已归还 → return_date；提示「{标签}为必填」。
  en: >
      Non-empty checks gated by dependency fields: involve_cost=是 → amount/cost_bearer, contract_type=其他 → contract_type_other, seal_type=证照借用 → cert_name, return_status=已归还 → return_date; message 「{标签}为必填」.
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:10:00Z"
fingerprint: pending
source:
  - path: "doc/forms.md"
    line: 47
    end_line: 47
  - path: "doc/forms.md"
    line: 82
    end_line: 84
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/{form_type}/validate/conditional"
    description:
      zh: >
          求值条件必填并校验。
      en: >
          Evaluates conditional requiredness and validates it.
deps:
  - kind: call
    to: oa.form.template.render.linkage
    from_api: "POST /api/v1/forms/{form_type}/validate/conditional"
    to_api: "POST /api/v1/forms/render/{form_type}/linkage"
    label: {zh: "复用联动求值", en: "Reuses linkage evaluation"}
---
