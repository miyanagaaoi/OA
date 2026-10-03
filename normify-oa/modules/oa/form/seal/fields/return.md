---
uid: 8c080a27
id: oa.form.seal.fields.return
parent: oa.form.seal.fields
name: {zh: "归还字段组", en: "Return Field Group"}
description:
  zh: >
      归还状态 return_status（select、必填、默认未归还、审批中可改、仅归档节点可改）与归还时间 return_date（datetime、return_status=已归还 时必填、审批中可改）。
      
  en: >
      Return status `return_status` (select, required, default pending, editable during approval but only by the archive node) and return time `return_date` (datetime, required when the status is returned, editable during approval).
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.031Z"
fingerprint: c7857efac14024c3e4fad74a26f561fbd8d2ce636ea89207a9b2dabb4bf6fc4b
source:
  - path: "doc/forms.md"
  - path: "oa-server/src/main/java/com/oa/form/seal/SealFormRules.java"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/seal/field-groups/return"
    description:
      zh: >
          归还字段组定义。
          
      en: >
          Return field group definition.
          
  - protocol: http
    method: PUT
    path: "/api/v1/forms/seal/instances/{instance_id}/return-status"
    description:
      zh: >
          更新归还状态（白名单内可改）。
          
      en: >
          Updates the return status within its whitelist.
          
deps:
  - kind: reference
    to: oa.form.dict.seal-cert.return-status
    from_api: "GET /api/v1/forms/seal/field-groups/return"
    to_api: "GET /api/v1/forms/dicts/return-status/items"
    label: {zh: "归还状态取值来源", en: "Return status options"}
---

## 证据锚点
- `doc/forms.md` → `### 6.6 归还状态（字段 code `return_status` · 字典类型 `return_status`）`（§6.6 归还状态）
