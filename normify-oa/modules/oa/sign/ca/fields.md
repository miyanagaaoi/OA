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
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.789Z"
fingerprint: 6be246c6834b9b56d4a42d5b955150bb6a06a766a90c031fd7f3af6b4c917a6d
source:
  - path: "doc/prd-0.1.md"
    line: 370
    end_line: 374
  - path: "doc/data-model.md"
    line: 540
    end_line: 553
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
