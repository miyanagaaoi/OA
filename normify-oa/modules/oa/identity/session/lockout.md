---
uid: 2306de18
id: oa.identity.session.lockout
parent: oa.identity.session
name: {zh: "登录失败锁定", en: "Login Failure Lockout"}
description:
  zh: >
      密码连续失败 5 次锁定账号 15 分钟，锁定期间登录直接拒绝并在日志中记失败原因 locked；管理员可在留痕下解锁。
      
  en: >
      Five consecutive failed passwords lock the account for 15 minutes; logins are rejected while locked with the reason recorded as locked, and an admin may unlock with a trace.
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.347Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/auth/lock-status"
    description:
      zh: >
          查询账号锁定状态与剩余时长。
          
      en: >
          Reads lock state and remaining time.
          
  - protocol: http
    method: POST
    path: "/api/v1/auth/unlock"
    description:
      zh: >
          管理员解锁账号（留痕）。
          
      en: >
          Admin unlocks an account with a trace.
          
  - protocol: redis
    path: "auth:fail:{account}"
    description:
      zh: >
          失败计数与锁定标记缓存键。
          
      en: >
          Failure counter and lock flag cache key.
          
deps:
  - kind: call
    to: oa.identity.user.profile
    from_api: "POST /api/v1/auth/unlock"
    to_api: "GET /api/v1/identity/users"
    label: {zh: "解锁前校验账号", en: "Verify account status"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-NFR-005`（§第9章 非功能需求）
