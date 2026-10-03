---
uid: 24973b86
id: oa.platform.security.crypto.hash
parent: oa.platform.security.crypto
state: planned
name: {zh: "密码哈希", en: "Password Hashing"}
description:
  zh: >
      密码哈希策略：加盐、慢哈希参数，以及成本因子变更时的平滑升级路径。
      
  en: >
      Password hashing policy: salted, slow hash parameters and an upgrade path when the cost factor changes.
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.642Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: file
    path: "config/crypto/hash-policy.yml"
    description:
      zh: >
          加盐哈希参数与登录时自动升级的规则配置。
          
      en: >
          Salted hash parameters and rehash-on-login upgrade rule.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-NFR-005`（§第9章 非功能需求）
