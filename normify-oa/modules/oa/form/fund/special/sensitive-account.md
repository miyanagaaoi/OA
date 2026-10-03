---
uid: 49bce4b7
id: oa.form.fund.special.sensitive-account
parent: oa.form.fund.special
state: planned
name: {zh: "收款账号加密与脱敏", en: "Payee Account Encryption & Masking"}
description:
  zh: >
      收款账号为敏感字段：以加密形式存储（非明文）；列表与详情默认脱敏为 `****1234`；完整值仅财务角色与系统管理员可见，且不可导出。
      
  en: >
      The payee account is sensitive: stored encrypted rather than in clear text; masked as `****1234` in lists and detail; the full value is visible only to finance roles and admins and is never exportable.
      
revision: 966907fad0c5f0d01bc6a76ddba80bbbf67f586a
updated_at: "2026-10-03T04:41:37.349Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/fund/instances/{instance_id}/fields/payee-account"
    description:
      zh: >
          按角色返回脱敏或完整收款账号。
          
      en: >
          Returns the masked or full payee account per role.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/fund/fields/payee-account/mask"
    description:
      zh: >
          生成脱敏展示值（****1234）。
          
      en: >
          Builds the masked display value (****1234).
          
deps:
  - kind: reference
    to: oa.authz.visibility
    from_api: "GET /api/v1/forms/fund/instances/{instance_id}/fields/payee-account"
    label: {zh: "角色可见性规则", en: "Role visibility rules"}
  - kind: reference
    to: oa.platform.security
    from_api: "POST /api/v1/forms/fund/fields/payee-account/mask"
    label: {zh: "加密存储与密钥", en: "Encryption and key handling"}
---

## 证据锚点
- `doc/forms.md` → `## 3. 资金审批单（`form_type = fund`）`（§3. 资金审批单）
