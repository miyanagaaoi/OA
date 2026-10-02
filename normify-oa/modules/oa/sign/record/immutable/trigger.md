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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.760Z"
fingerprint: 7241121ccd161ffddfd3e9166a1a5bc6bd672932d1333daa0959b62334d40745
source:
  - path: "doc/data-model.md"
    line: 745
    end_line: 773
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
