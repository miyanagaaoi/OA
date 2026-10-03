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
      
revision: c22d447e6e63ccb0edfd9624026f21e8d1413077
updated_at: "2026-10-03T02:20:07.665Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
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
