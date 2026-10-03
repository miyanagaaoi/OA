---
uid: 59dd3f97
id: oa.platform.security.password.lockout
parent: oa.platform.security.password
state: planned
name: {zh: "失败锁定", en: "Lockout Control"}
description:
  zh: >
      失败计数与锁定：连续失败 5 次锁定账号 15 分钟，管理员可查询当前锁定清单。
      
  en: >
      Failure counting and lockout: five consecutive failures lock the account for fifteen minutes, with lockouts listed for administrators.
      
revision: 966907fad0c5f0d01bc6a76ddba80bbbf67f586a
updated_at: "2026-10-03T04:41:37.400Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/security/lockouts"
    description:
      zh: >
          查询当前锁定账号及其失败次数。
          
      en: >
          Lists currently locked accounts with the failure count.
          
  - protocol: file
    path: "reports/security/lockouts-{date}.csv"
    description:
      zh: >
          按日导出锁定与解锁事件供复核。
          
      en: >
          Daily export of lockout and unlock events for review.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-NFR-005`（§第9章 非功能需求）
