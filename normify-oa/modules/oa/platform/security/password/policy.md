---
uid: 51060cbc
id: oa.platform.security.password.policy
parent: oa.platform.security.password
name: {zh: "密码复杂度策略", en: "Password Policy"}
description:
  zh: >
      复杂度策略（8 位以上且含字母与数字）、改密流程与不可复用规则，全部服务端强制。
      
  en: >
      Complexity policy (at least eight characters with letters and digits), change-password flow and reuse rules enforced server-side.
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.374Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-NFR-005`（§第9章 非功能需求）
