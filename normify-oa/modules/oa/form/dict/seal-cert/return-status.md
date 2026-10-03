---
uid: 6079521f
id: oa.form.dict.seal-cert.return-status
parent: oa.form.dict.seal-cert
name: {zh: "归还状态字典", en: "Return Status Dictionary"}
description:
  zh: >
      归还状态 return_status：pending 未归还（默认）/ returned 已归还 / not_required 无需归还；仅归档节点（⑦）可改，改「已归还」需填归还时间。
      
  en: >
      Return status `return_status`: pending (default), returned and not required; only the archive node (⑦) may change it, and returned requires a return time.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.009Z"
fingerprint: 10c6effd215627f1c844a3acecef0995e0cae341763861c18553845cb71394d6
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
