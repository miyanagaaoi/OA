---
uid: a83c5e7b
id: oa.identity.user.initial-password
parent: oa.identity.user
name: {zh: "初始口令生成", en: "Initial Password Generation"}
description:
  zh: >
      新增人员与批量导入（user.csv 第 ② 步）共用的初始口令生成：随机 ≥8 位且**必然**同时含字母与数字（REQ-NFR-005），生成后再用口令策略自校验；口令只在本次响应返回一次，落库只存 BCrypt 哈希，首次登录强制改密（import-spec T-03），不通过邮件明文发送。
      
  en: >
      Initial-password generation shared by manual user creation and the user.csv bulk import: random, at least 8 characters and guaranteed to contain both letters and digits (REQ-NFR-005), then self-checked against the password policy; returned exactly once in the response, stored only as a BCrypt hash, with forced change on first login (import-spec T-03) and never mailed in clear text.
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.351Z"
fingerprint: 79970c212718a599d15647cd2da192d40d444c22f36c9471b7097ebfdffe241c
source:
  - path: "oa-server/src/main/java/com/oa/identity/app/InitialPasswordGenerator.java"
apis:
  - protocol: rpc
    path: "identity.user.generateInitialPassword"
    description:
      zh: >
          生成并自校验初始口令（一次性下发）。
          
      en: >
          Generates and self-validates an initial password for one-time hand-over.
          
deps:
  - kind: reference
    to: oa.identity.user.profile
    from_api: "rpc:identity.user.generateInitialPassword"
    to_api: "POST /api/v1/identity/users"
    label: {zh: "人员新增与导入共用", en: "Shared by create and import"}
---
