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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.739Z"
fingerprint: 1ebf4aee4ac8648bfaec0ea0afbfecc1b3e2f5fecc0dff532be914d9e85be0f2
source:
  - path: "doc/data-model.md"
    line: 143
    end_line: 159
  - path: "doc/prd-0.1.md"
    line: 422
    end_line: 431
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
