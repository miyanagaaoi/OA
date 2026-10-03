---
uid: 137904d4
id: oa.form.template.validate.conditional
parent: oa.form.template.validate
name: {zh: "条件必填校验", en: "Conditional Required Validation"}
description:
  zh: >
      依赖字段满足条件时的非空校验：involve_cost=是 → amount/cost_bearer、contract_type=其他 → contract_type_other、seal_type=证照借用 → cert_name、return_status=已归还 → return_date；提示「{标签}为必填」。
      
  en: >
      Non-empty checks gated by dependency fields: involve_cost=是 → amount/cost_bearer, contract_type=其他 → contract_type_other, seal_type=证照借用 → cert_name, return_status=已归还 → return_date; message 「{标签}为必填」.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.048Z"
fingerprint: e9b163db3e4378d7966ad0a76b135de7130c3cdecdf6acdba494c2fcad2c1a46
source:
  - path: "doc/forms.md"
  - path: "doc/forms.md"
  - path: "oa-server/src/main/java/com/oa/form/template/validate/ConditionEvaluator.java"
  - path: "oa-server/src/main/java/com/oa/form/template/validate/FormPayloadValidator.java"
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

## 证据锚点
- `doc/forms.md` → `### 1.3 通用校验规则`（§1.3 通用校验规则）
