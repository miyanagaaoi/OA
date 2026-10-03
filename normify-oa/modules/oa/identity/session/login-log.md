---
uid: 390ba667
id: oa.identity.session.login-log
parent: oa.identity.session
name: {zh: "登录日志", en: "Login Log"}
description:
  zh: >
      记录每次登录的时间、IP、设备信息、成功/失败与失败原因（bad_password/locked/disabled），失败也记录尝试账号；保留 1 年、只追加不可改删（REQ-LOG-005 的产生侧）。
      
  en: >
      Logs each login attempt with time, IP, device info, success/failure and failure reason (bad_password/locked/disabled), recording the attempted account even on failure; append-only, kept for one year.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.239Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/data-model.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/auth/login-logs"
    description:
      zh: >
          查询登录日志（管理员）。
          
      en: >
          Queries login logs (admin).
          
  - protocol: http
    method: POST
    path: "/internal/auth/login-logs"
    description:
      zh: >
          内部写入一条登录日志。
          
      en: >
          Internal endpoint appending a login log.
          
  - protocol: mysql
    path: "sys_login_log"
    description:
      zh: >
          登录日志表。
          
      en: >
          Login log table.
          
deps:
  - kind: dataflow
    to: oa.audit.security
    label: {zh: "登录失败与锁定事件进入安全审计", en: "Push login security events"}
---

## 证据锚点
- `doc/data-model.md` → `CREATE TABLE sys_login_log`（§2. 身份与组织）
- `doc/prd-0.1.md` → `REQ-LOG-005`（§6.9 审计日志）
