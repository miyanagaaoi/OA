---
uid: 8eeb66d8
id: oa.platform.security.crypto
parent: oa.platform.security
name: {zh: "敏感字段加密", en: "Sensitive Field Encryption"}
description:
  zh: >
      密码加盐哈希存储、手机号加密落库，密钥可托管与轮换；列表响应仍只返回脱敏值。
      
  en: >
      Passwords are stored as salted hashes and phone numbers are encrypted at rest with managed keys and a rotation procedure, while list responses keep masked values.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.101Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-NFR-005`（§第9章 非功能需求）
