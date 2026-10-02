---
uid: 51060cbc
id: oa.platform.security.password.policy
parent: oa.platform.security.password
state: planned
name: {zh: "密码复杂度策略", en: "Password Policy"}
description:
  zh: >
      复杂度策略（8 位以上且含字母与数字）、改密流程与不可复用规则，全部服务端强制。
      
  en: >
      Complexity policy (at least eight characters with letters and digits), change-password flow and reuse rules enforced server-side.
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.770Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 535
    end_line: 535
apis:
  - protocol: file
    path: "config/security/password-policy.yml"
    description:
      zh: >
          设置密码时应用的复杂度、历史与有效期规则。
          
      en: >
          Complexity, history and expiry rules applied when a password is set.
          
  - protocol: http
    method: POST
    path: "/api/v1/auth/password"
    description:
      zh: >
          通过策略校验后修改本人密码。
          
      en: >
          Changes the caller's password after policy validation.
          
---
