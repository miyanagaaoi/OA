---
uid: 4167977f
id: oa.form.fund.fields.payee
parent: oa.form.fund.fields
state: planned
name: {zh: "收款方字段组", en: "Payee Field Group"}
description:
  zh: >
      收款方名称 payee（text≤100、必填）与收款账号 payee_account（text≤40、必填、仅数字/字母/`-`、加密存储）；账号在列表与详情默认脱敏，仅财务角色与系统管理员可见完整值。
      
  en: >
      Payee name `payee` (text ≤100, required) and payee account `payee_account` (text ≤40, required, digits/letters/`-` only, encrypted at rest); the account is masked in lists and detail, with the full value only for finance roles and admins.
      
revision: 112ab0a1d46779714029044fc8e0b46627804f30
updated_at: "2026-10-03T01:51:21.580Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
    line: 107
    end_line: 108
  - path: "doc/forms.md"
    line: 118
    end_line: 118
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/fund/field-groups/payee"
    description:
      zh: >
          收款方与账号字段组定义。
          
      en: >
          Payee and account field group definition.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/fund/fields/payee-account/verify"
    description:
      zh: >
          校验收款账号字符集（数字/字母/-）。
          
      en: >
          Validates the account character set (digits/letters/-).
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/fund/fields/payee-account/encrypt"
    description:
      zh: >
          收款账号加密后存储。
          
      en: >
          Encrypts the payee account before storage.
          
deps:
  - kind: reference
    to: oa.platform.security
    from_api: "POST /api/v1/forms/fund/fields/payee-account/encrypt"
    label: {zh: "敏感字段加密存储", en: "Encrypt sensitive fields"}
---
