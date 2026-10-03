---
uid: 4e5b7086
id: oa.sign
parent: oa
state: planned
name: {zh: "电子签名", en: "Electronic Signature"}
description:
  zh: >
      手写签名采集与预存、节点签名策略（强制/可选/不签名）、签名记录与单据绑定且只追加不可删改，并为二期 CA 可靠电子签名预留字段（sign_type / ca_* / verify_result）。
      
  en: >
      Handwritten signature capture and presets, per-node signature policy, append-only signature records bound to documents, and reserved fields for a future CA-grade signature.
      
revision: 966907fad0c5f0d01bc6a76ddba80bbbf67f586a
updated_at: "2026-10-03T04:41:37.421Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-SIGN-001`（§6.5 电子签名与身份确认）
- `doc/data-model.md` → `CREATE TABLE flow_signature`（§6. 签名、附件、抄送、消息、审计）
