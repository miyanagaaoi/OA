---
uid: 5064ac25
id: oa.platform.security.crypto.keys
parent: oa.platform.security.crypto
name: {zh: "密钥管理", en: "Key Management"}
description:
  zh: >
      密钥托管与轮换：API 不返回密钥本体，轮换显式触发且状态可审计。
      
  en: >
      Key custody and rotation: keys are never returned by the API, rotation is triggered explicitly and its status is auditable.
      
revision: e3b34a3c59417096ade647fab4261b06f6b605e2
updated_at: "2026-10-03T02:30:28.767Z"
fingerprint: cd388c1b63faa986678e023bd356b3a20488b7cce089442d72f3e834e463e0a7
source:
  - path: "oa-server/src/main/java/com/oa/platform/security/crypto/PhoneCryptoService.java"
  - path: "oa-server/src/main/java/com/oa/platform/security/api/CryptoAdminController.java"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/keys"
    description:
      zh: >
          查询密钥状态（活动 keyId + 可用 keyId 列表，永不下发密钥本体）。
          
      en: >
          Reads key status without exposing key material.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/keys/rotate"
    description:
      zh: >
          密钥轮换：把非活动 keyId 的密文收敛到活动密钥（幂等）。
          
      en: >
          Converges ciphertext onto the active key (idempotent).
          
---
