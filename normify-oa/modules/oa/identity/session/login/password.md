---
uid: 23a68186
id: oa.identity.session.login.password
parent: oa.identity.session.login
name: {zh: "密码策略与哈希校验", en: "Password Policy & Hash Verify"}
description:
  zh: >
      密码复杂度（8 位以上且含字母与数字）校验、加盐哈希比对与修改密码；密码与手机号加密存储，永不明文落库。
      
  en: >
      Password complexity (8+ characters with letters and digits) validation, salted-hash comparison and password change; passwords and phones are encrypted at rest and never stored in clear text.
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.741Z"
fingerprint: 1ebf4aee4ac8648bfaec0ea0afbfecc1b3e2f5fecc0dff532be914d9e85be0f2
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
