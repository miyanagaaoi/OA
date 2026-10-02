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
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.797Z"
fingerprint: 6be246c6834b9b56d4a42d5b955150bb6a06a766a90c031fd7f3af6b4c917a6d
source:
  - path: "doc/prd-0.1.md"
    line: 374
    end_line: 374
  - path: "doc/data-model.md"
    line: 546
    end_line: 546
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
