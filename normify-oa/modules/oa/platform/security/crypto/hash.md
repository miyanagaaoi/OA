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
      
revision: 112ab0a1d46779714029044fc8e0b46627804f30
updated_at: "2026-10-03T01:51:21.631Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 535
    end_line: 535
apis:
  - protocol: file
    path: "config/crypto/hash-policy.yml"
    description:
      zh: >
          加盐哈希参数与登录时自动升级的规则配置。
          
      en: >
          Salted hash parameters and rehash-on-login upgrade rule.
          
---
