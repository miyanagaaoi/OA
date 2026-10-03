---
uid: 69d42b4f
id: oa.form.contract.fields.counterparty
parent: oa.form.contract.fields
name: {zh: "对方主体字段组", en: "Counterparty Field Group"}
description:
  zh: >
      对方主体名称 counterparty（text≤100、必填）与对方统一社会信用代码 counterparty_credit（text≤18、必填、18 位数字+大写字母）；代码做格式校验。
      
  en: >
      Counterparty name `counterparty` (text ≤100, required) and unified social credit code `counterparty_credit` (text ≤18, required, 18 characters of digits and uppercase letters) with format validation.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.258Z"
fingerprint: 1266f6407e434b7ee473c37529842b35c2605a39bc4ad8881f6bc19d40d4caf1
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
