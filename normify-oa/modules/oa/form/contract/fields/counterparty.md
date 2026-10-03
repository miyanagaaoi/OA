---
uid: 69d42b4f
id: oa.form.contract.fields.counterparty
parent: oa.form.contract.fields
state: planned
name: {zh: "对方主体字段组", en: "Counterparty Field Group"}
description:
  zh: >
      对方主体名称 counterparty（text≤100、必填）与对方统一社会信用代码 counterparty_credit（text≤18、必填、18 位数字+大写字母）；代码做格式校验。
      
  en: >
      Counterparty name `counterparty` (text ≤100, required) and unified social credit code `counterparty_credit` (text ≤18, required, 18 characters of digits and uppercase letters) with format validation.
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.338Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/contract/field-groups/counterparty"
    description:
      zh: >
          对方主体字段组定义。
          
      en: >
          Counterparty field group definition.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/contract/fields/counterparty-credit/verify"
    description:
      zh: >
          校验统一社会信用代码格式。
          
      en: >
          Validates the unified social credit code format.
          
deps:
  - kind: reference
    to: oa.integration.masterdata
    from_api: "POST /api/v1/forms/contract/fields/counterparty-credit/verify"
    label: {zh: "对方主体主数据", en: "Counterparty master data"}
---

## 证据锚点
- `doc/forms.md` → `## 4. 合同审批单（`form_type = contract`）`（§4. 合同审批单）
