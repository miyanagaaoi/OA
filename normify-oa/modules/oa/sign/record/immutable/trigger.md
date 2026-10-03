---
uid: 407abd75
id: oa.sign.record.immutable.trigger
parent: oa.sign.record.immutable
name: {zh: "数据库不可变触发器", en: "Append-Only DB Triggers"}
description:
  zh: >
      MySQL 8.0 触发器：trg_flow_signature_no_update 仅放行验签结果回写（sign_image / hash / user_id / signed_at 未变），trg_flow_signature_no_delete 一律 SIGNAL 拒绝；触发器是 AC-20 的验收对象。
      
  en: >
      MySQL 8.0 triggers: no_update allows only the verify-result write-back (image, hash, user and sign time unchanged) while no_delete always raises SIGNAL; these triggers are the acceptance object of AC-20.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.133Z"
fingerprint: d27f073aa0b7d919258285e22376dd379c55e1e74d7e28c48440b1100569087a
source:
  - path: "doc/data-model.md"
apis:
  - protocol: rpc
    path: "db.trigger.flow_signature.no_update"
    description:
      zh: >
          拒绝修改签名记录的触发器（仅放行验签回写）。
          
      en: >
          Trigger rejecting updates to signature records except the verify-result write-back.
          
  - protocol: rpc
    path: "db.trigger.flow_signature.no_delete"
    description:
      zh: >
          拒绝删除签名记录的触发器。
          
      en: >
          Trigger rejecting deletes of signature records.
          
deps:
  - kind: reference
    to: oa.sign.record.append
    to_api: "mysql:flow_signature"
    label: {zh: "保护的签名记录表", en: "Protected signature table"}
---

## 证据锚点
- `doc/data-model.md` → `### 8.1 审计与签名的不可变约束（对应 AC-20）`（§8.1 审计与签名的不可变约束）
