---
uid: 8eeb66d8
id: oa.platform.security.crypto
parent: oa.platform.security
state: planned
name: {zh: "敏感字段加密", en: "Sensitive Field Encryption"}
description:
  zh: >
      密码加盐哈希存储、手机号加密落库，密钥可托管与轮换；列表响应仍只返回脱敏值。
      
  en: >
      Passwords are stored as salted hashes and phone numbers are encrypted at rest with managed keys and a rotation procedure, while list responses keep masked values.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.272Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-NFR-005`（§第9章 非功能需求）
