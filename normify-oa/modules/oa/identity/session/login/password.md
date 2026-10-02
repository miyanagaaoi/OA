---
uid: 23a68186
id: oa.identity.session.login.password
parent: oa.identity.session.login
state: planned
name: {zh: "密码策略与哈希校验", en: "Password Policy & Hash Verify"}
description:
  zh: >
      密码复杂度（8 位以上且含字母与数字）校验、加盐哈希比对与修改密码；密码与手机号加密存储，永不明文落库。
      
  en: >
      Password complexity (8+ characters with letters and digits) validation, salted-hash comparison and password change; passwords and phones are encrypted at rest and never stored in clear text.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.691Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
source:
  - path: "doc/prd-0.1.md"
    line: 525
    end_line: 541
  - path: "doc/data-model.md"
    line: 59
    end_line: 83
apis:
  - protocol: http
    method: POST
    path: "/api/v1/auth/password/verify"
    description:
      zh: >
          校验账号密码（含复杂度前置校验）。
          
      en: >
          Verifies account and password.
          
  - protocol: http
    method: PUT
    path: "/api/v1/auth/password"
    description:
      zh: >
          修改本人密码。
          
      en: >
          Changes the caller's password.
          
  - protocol: http
    method: GET
    path: "/api/v1/auth/password-policy"
    description:
      zh: >
          读取密码复杂度策略。
          
      en: >
          Reads the password policy.
          
deps:
  - kind: call
    to: oa.identity.user.profile
    from_api: "PUT /api/v1/auth/password"
    to_api: "PUT /api/v1/identity/users/{id}/password"
    label: {zh: "回写新密码哈希", en: "Write new password hash"}
  - kind: call
    to: oa.identity.session.lockout
    from_api: "POST /api/v1/auth/password/verify"
    to_api: "GET /api/v1/auth/lock-status"
    label: {zh: "失败计数与锁定判定", en: "Failure count and lock"}
---
