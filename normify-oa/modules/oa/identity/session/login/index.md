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
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.240Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-USER-002`（§6.8 移动端 H5 与登录保持）
- `doc/data-model.md` → `CREATE TABLE sys_user_session`（§2. 身份与组织）
