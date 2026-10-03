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
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.567Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
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
