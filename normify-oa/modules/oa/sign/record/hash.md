---
uid: 3252e90f
id: oa.sign.record.hash
parent: oa.sign.record
state: planned
name: {zh: "防篡改哈希", en: "Tamper-Evident Hash"}
description:
  zh: >
      计算签名记录防篡改哈希：覆盖本表全部业务字段（含 CA 预留字段 ca_signature / ca_cert_serial / ca_issuer / tsa_source / verify_result），保证二期接入 CA 后无需重建历史签名；并支持对存量记录重算校验。
      
  en: >
      Computes the tamper-evident hash over all business fields including the reserved CA fields, so historical signatures need no rebuild after CA integration, and re-verifies existing records.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.590Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/sign/records/hash"
    description:
      zh: >
          计算签名记录哈希（覆盖 CA 预留字段）。
          
      en: >
          Computes the signature record hash including the reserved CA fields.
          
  - protocol: http
    method: GET
    path: "/api/v1/sign/records/{id}/hash-verify"
    description:
      zh: >
          重算并校验现存签名记录的哈希。
          
      en: >
          Recomputes and verifies the stored hash of a signature record.
          
deps:
  - kind: reference
    to: oa.sign.ca.fields
    to_api: "GET /api/v1/sign/ca/schema"
    label: {zh: "哈希覆盖 CA 预留字段", en: "Hash covers reserved CA fields"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-SIGN-004`（§6.5 电子签名与身份确认）
- `doc/data-model.md` → `CREATE TABLE flow_signature`（§6. 签名、附件、抄送、消息、审计）
