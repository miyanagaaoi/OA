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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.661Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
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
