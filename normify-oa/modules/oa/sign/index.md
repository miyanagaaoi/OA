---
uid: 4e5b7086
id: oa.sign
parent: oa
name: {zh: "电子签名", en: "Electronic Signature"}
description:
  zh: >
      手写签名采集与预存、节点签名策略（强制/可选/不签名）、签名记录与单据绑定且只追加不可删改，并为二期 CA 可靠电子签名预留字段（sign_type / ca_* / verify_result）。
      
  en: >
      Handwritten signature capture and presets, per-node signature policy, append-only signature records bound to documents, and reserved fields for a future CA-grade signature.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.353Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-SIGN-001`（§6.5 电子签名与身份确认）
- `doc/data-model.md` → `CREATE TABLE flow_signature`（§6. 签名、附件、抄送、消息、审计）
