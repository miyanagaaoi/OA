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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.656Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
source:
  - path: "doc/forms.md"
    line: 134
    end_line: 135
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
