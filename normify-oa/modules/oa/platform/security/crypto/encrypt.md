---
uid: 29a35127
id: oa.platform.security.crypto.encrypt
parent: oa.platform.security.crypto
name: {zh: "字段级加密", en: "Field Encryption"}
description:
  zh: >
      手机号等个人信息的字段级加密；默认返回脱敏值，仅本人与系统管理员可取完整值。
      
  en: >
      Field-level encryption for phone numbers and other PII, with masked values returned by default and full values only for the owner and administrators.
      
revision: 810e68992bef8dea7b5d5a5b319ada301b87f9b0
updated_at: "2026-10-03T01:05:54.722Z"
fingerprint: c103d3b6295f4a12d2328832a957ca67208b438ec54a683088ac5bcb0a6b38e0
source:
  - path: "oa-server/src/main/java/com/oa/platform/security/crypto/PhoneCipher.java"
  - path: "oa-server/src/main/java/com/oa/platform/security/crypto/PhoneCryptoService.java"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/crypto/fields"
    description:
      zh: >
          查询已加密字段及其当前脱敏方式（不返回密钥本体）。
          
      en: >
          Lists encrypted fields and their current masking mode.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/crypto/phone-migrate"
    description:
      zh: >
          一次性把库中历史明文手机号加密为密文（幂等）。
          
      en: >
          One-off idempotent migration of legacy clear-text phones to ciphertext.
          
  - protocol: rpc
    path: "crypto.phone.encryptForStore"
    description:
      zh: >
          落库前加密手机号（v1:<keyId>:<iv||ct||tag>）。
          
      en: >
          Encrypts a phone for storage (v1:keyId:iv+ct+tag).
          
  - protocol: rpc
    path: "crypto.phone.decrypt"
    description:
      zh: >
          解密库中手机号；未知 keyId 或被篡改一律 fail-closed。
          
      en: >
          Decrypts a stored phone; unknown keyId or tampering fails closed.
          
---
