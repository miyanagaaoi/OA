---
uid: 231a1099
id: oa.identity.session.login
parent: oa.identity.session
name: {zh: "登录与凭证校验", en: "Login & Credential Check"}
description:
  zh: >
      登录编排层：账号密码校验、失败计数与锁定联动、签发会话并记录登录结果，桌面端与 H5 共用同一入口。
      
  en: >
      Login orchestration: credential verification, failure counting and lockout linkage, session issuing and login logging, shared by desktop and H5.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.365Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-USER-002`（§6.8 移动端 H5 与登录保持）
- `doc/data-model.md` → `CREATE TABLE sys_user_session`（§2. 身份与组织）
