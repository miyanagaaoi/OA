---
uid: 4b87abb0
id: oa.sign.ca.fields
parent: oa.sign.ca
state: planned
name: {zh: "CA 预留字段模型", en: "Reserved CA Fields"}
description:
  zh: >
      一期即落地的 CA 预留字段：sign_type（手写/CA）、ca_signature、ca_cert_serial、ca_issuer、tsa_source、verify_result、verified_at；一期保持 NULL 但结构不可省略，且纳入签名哈希与不可篡改范围，保证二期接入 CA 时无需重建历史签名。
      
  en: >
      CA reservation fields created in phase one: sign_type, ca_signature, ca_cert_serial, ca_issuer, tsa_source, verify_result and verified_at; they stay NULL but cannot be omitted and are inside the hash and immutability scope.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.583Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/sign/ca/schema"
    description:
      zh: >
          返回 CA 预留字段清单与语义。
          
      en: >
          Returns the reserved CA field list with semantics.
          
  - protocol: http
    method: GET
    path: "/api/v1/sign/ca/readiness"
    description:
      zh: >
          一期占位状态：字段已建、取值保持为空。
          
      en: >
          Phase-one placeholder status: fields exist and stay empty.
          
deps:
  - kind: reference
    to: oa.audit.integrity
    label: {zh: "预留字段纳入不可篡改范围", en: "Reserved fields immutable"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-SIGN-005`（§6.5 电子签名与身份确认）
- `doc/data-model.md` → `CREATE TABLE flow_signature`（§6. 签名、附件、抄送、消息、审计）
