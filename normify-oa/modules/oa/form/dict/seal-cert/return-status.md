---
uid: 6079521f
id: oa.form.dict.seal-cert.return-status
parent: oa.form.dict.seal-cert
state: planned
name: {zh: "归还状态字典", en: "Return Status Dictionary"}
description:
  zh: >
      归还状态 return_status：pending 未归还（默认）/ returned 已归还 / not_required 无需归还；仅归档节点（⑦）可改，改「已归还」需填归还时间。
      
  en: >
      Return status `return_status`: pending (default), returned and not required; only the archive node (⑦) may change it, and returned requires a return time.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.194Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/dicts/return-status/items"
    description:
      zh: >
          归还状态可选值列表。
          
      en: >
          Lists selectable return statuses.
          
---

## 证据锚点
- `doc/forms.md` → `### 6.6 归还状态（字段 code `return_status` · 字典类型 `return_status`）`（§6.6 归还状态）
