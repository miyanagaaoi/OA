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
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.643Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-NFR-005`（§第9章 非功能需求）
