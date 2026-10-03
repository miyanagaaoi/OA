---
uid: 407abd75
id: oa.sign.record.immutable.trigger
parent: oa.sign.record.immutable
state: planned
name: {zh: "数据库不可变触发器", en: "Append-Only DB Triggers"}
description:
  zh: >
      MySQL 8.0 触发器：trg_flow_signature_no_update 仅放行验签结果回写（sign_image / hash / user_id / signed_at 未变），trg_flow_signature_no_delete 一律 SIGNAL 拒绝；触发器是 AC-20 的验收对象。
      
  en: >
      MySQL 8.0 triggers: no_update allows only the verify-result write-back (image, hash, user and sign time unchanged) while no_delete always raises SIGNAL; these triggers are the acceptance object of AC-20.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.415Z"
fingerprint: 4e545cc1c566ce9e10c8fb0b82fc64bfd49ae277034ea19e5092531bb0c1231e
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
