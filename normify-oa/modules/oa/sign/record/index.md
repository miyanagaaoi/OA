---
uid: 4f5b7089
id: oa.sign.record
parent: oa.sign
state: planned
name: {zh: "签名记录与不可篡改", en: "Signature Records"}
description:
  zh: >
      节点签名策略（强制/可选/不签名，默认集团分管领导与董事长强制）与只追加的签名记录：签名图、时间戳、审批人、设备指纹、IP 与哈希，与审批单绑定存储，写入后不可修改删除，重新签署保留历史版本。
      
  en: >
      Per-node signature policy (mandatory/optional/none, with group line leader and chairman mandatory by default) and append-only signature records storing image, timestamp, approver, device fingerprint, IP and hash, bound to the document and never modified or deleted - re-signing adds a new version.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.303Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-SIGN-004`（§6.5 电子签名与身份确认）
- `doc/data-model.md` → `CREATE TABLE flow_signature`（§6. 签名、附件、抄送、消息、审计）
