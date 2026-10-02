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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.716Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
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
