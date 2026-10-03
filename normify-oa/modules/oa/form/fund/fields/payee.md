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
      
revision: 44fc7aba1c7e884ffa3553faf31bfce974b388a9
updated_at: "2026-10-03T04:12:20.706Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
  - path: "doc/forms.md"
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

## 证据锚点
- `doc/forms.md` → `## 3. 资金审批单（`form_type = fund`）`（§3. 资金审批单）
