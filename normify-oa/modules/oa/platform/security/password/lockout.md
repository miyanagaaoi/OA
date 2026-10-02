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
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.769Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 536
    end_line: 536
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
