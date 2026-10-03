---
uid: 72d58f83
id: oa.platform.security.password
parent: oa.platform.security
state: planned
name: {zh: "密码与锁定", en: "Password & Lockout"}
description:
  zh: >
      密码 8 位以上且含字母与数字、连续失败 5 次锁定 15 分钟、强制改密流程；登录结果与失败原因写入登录日志。
      
  en: >
      Password policy of at least eight characters mixing letters and digits, lockout for fifteen minutes after five failed attempts, and forced change flow, all written to the login log.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.567Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-NFR-005`（§第9章 非功能需求）
