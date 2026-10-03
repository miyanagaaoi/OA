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
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.264Z"
fingerprint: 1266f6407e434b7ee473c37529842b35c2605a39bc4ad8881f6bc19d40d4caf1
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
