---
uid: 8c080a27
id: oa.form.seal.fields.return
parent: oa.form.seal.fields
state: planned
name: {zh: "归还字段组", en: "Return Field Group"}
description:
  zh: >
      归还状态 return_status（select、必填、默认未归还、审批中可改、仅归档节点可改）与归还时间 return_date（datetime、return_status=已归还 时必填、审批中可改）。
      
  en: >
      Return status `return_status` (select, required, default pending, editable during approval but only by the archive node) and return time `return_date` (datetime, required when the status is returned, editable during approval).
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.221Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
    line: 166
    end_line: 167
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
