---
uid: 15dae069
id: oa.form.template.write-model.field-permission
parent: oa.form.template.write-model
name: {zh: "字段级权限与脱敏", en: "Field-Level Permission & Masking"}
description:
  zh: >
      金额与账号类字段按角色脱敏/隐藏：合同金额与资金金额对非财务角色只读展示且不可导出（导出仅系统管理员）；收款账号默认脱敏 `****1234`，完整值仅财务角色与系统管理员可见。一期不做字段级白名单配置。
      
  en: >
      Masks or hides amount and account fields per role: contract and fund amounts are read-only and non-exportable for non-finance roles (export is admin-only); the payee account shows `****1234` by default with the full value only for finance roles and admins. Phase one hardcodes these rules without per-field whitelists.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.294Z"
fingerprint: 4e95671e1a2c50b3aee8304d8c7491551290dab30b221822f631ff992c5aea6c
source:
  - path: "doc/forms.md"
  - path: "doc/forms.md"
  - path: "doc/prd-0.1.md"
  - path: "oa-server/src/main/java/com/oa/form/app/FormWritePolicy.java"
  - path: "oa-server/src/main/java/com/oa/authz/visibility/FormFieldWriteGuard.java"
  - path: "oa-server/src/main/java/com/oa/authz/visibility/AmountFieldPolicy.java"
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

## 证据锚点
- `doc/forms.md` → `### 1.2 字段的三态读写模型（**核心约束**）`（§1.2 字段的三态读写模型）
- `doc/prd-0.1.md` → `REQ-FLOW-023`（§6.3.1 集团层流转机制）
