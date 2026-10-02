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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.660Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
source:
  - path: "doc/forms.md"
    line: 235
    end_line: 241
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
