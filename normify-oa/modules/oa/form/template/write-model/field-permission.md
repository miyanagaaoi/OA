---
uid: 15dae069
id: oa.form.template.write-model.field-permission
parent: oa.form.template.write-model
state: planned
name: {zh: "字段级权限与脱敏", en: "Field-Level Permission & Masking"}
description:
  zh: >
      金额与账号类字段按角色脱敏/隐藏：合同金额与资金金额对非财务角色只读展示且不可导出（导出仅系统管理员）；收款账号默认脱敏 `****1234`，完整值仅财务角色与系统管理员可见。一期不做字段级白名单配置。
      
  en: >
      Masks or hides amount and account fields per role: contract and fund amounts are read-only and non-exportable for non-finance roles (export is admin-only); the payee account shows `****1234` by default with the full value only for finance roles and admins. Phase one hardcodes these rules without per-field whitelists.
      
revision: 112ab0a1d46779714029044fc8e0b46627804f30
updated_at: "2026-10-03T01:51:21.597Z"
fingerprint: a590145bc5717823c1c716f7cd29b05065b1aa8bf16dddb19dfc3b30d5207ee6
source:
  - path: "doc/forms.md"
    line: 68
    end_line: 68
  - path: "doc/forms.md"
    line: 118
    end_line: 118
  - path: "doc/prd-0.1.md"
    line: 192
    end_line: 198
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/instances/{instance_id}/fields/{field_id}/permission"
    description:
      zh: >
          判定字段对当前角色的可见与可导出权限。
          
      en: >
          Resolves a field's visibility and exportability for the current role.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/instances/{instance_id}/fields/mask"
    description:
      zh: >
          按角色生成脱敏后的单据字段视图。
          
      en: >
          Builds a role-masked view of the document's fields.
          
deps:
  - kind: reference
    to: oa.authz.visibility
    from_api: "GET /api/v1/forms/instances/{instance_id}/fields/{field_id}/permission"
    label: {zh: "角色可见性规则", en: "Role visibility rules"}
---
