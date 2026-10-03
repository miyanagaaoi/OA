---
uid: 4f5b708a
id: oa.sign.ca
parent: oa.sign
state: planned
name: {zh: "CA 升级预留", en: "CA Signature Reservations"}
description:
  zh: >
      为二期可靠电子签名预留：sign_type 区分手写/CA，预留 ca_signature / ca_cert_serial / ca_issuer / tsa_source 与 verify_result / verified_at 字段；记录哈希计算范围必须包含预留字段，接入 CA 后无需重建历史签名。
      
  en: >
      Reserved capability for a future CA-grade signature: signature type distinguishes handwritten from CA, reserved columns for signature value, certificate serial, issuer and trusted timestamp source plus verification result fields, and the record hash must already cover these reserved fields so history never needs rebuilding.
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.422Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-SIGN-005`（§6.5 电子签名与身份确认）
- `doc/data-model.md` → `CREATE TABLE flow_signature`（§6. 签名、附件、抄送、消息、审计）
