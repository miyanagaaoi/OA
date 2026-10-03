---
uid: 72d58f83
id: oa.platform.security.password
parent: oa.platform.security
name: {zh: "密码与锁定", en: "Password & Lockout"}
description:
  zh: >
      密码 8 位以上且含字母与数字、连续失败 5 次锁定 15 分钟、强制改密流程；登录结果与失败原因写入登录日志。
      
  en: >
      Password policy of at least eight characters mixing letters and digits, lockout for fifteen minutes after five failed attempts, and forced change flow, all written to the login log.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.333Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-NFR-005`（§第9章 非功能需求）
