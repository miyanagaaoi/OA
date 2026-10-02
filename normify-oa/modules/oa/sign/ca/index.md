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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.737Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
source:
  - path: "doc/prd-0.1.md"
    line: 370
    end_line: 376
  - path: "doc/data-model.md"
    line: 529
    end_line: 620
---
