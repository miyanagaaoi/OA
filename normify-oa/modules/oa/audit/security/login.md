---
uid: "1308e901"
id: oa.audit.security.login
parent: oa.audit.security
state: planned
name: {zh: "登录日志", en: "Login Log"}
description:
  zh: >
      记录登录与登出时间、来源 IP、设备信息与失败原因，覆盖多设备登录、超限踢出与失败锁定场景，日志保留 1 年。
      
  en: >
      Records login and logout time, source IP, device information and failure reason, covering multi-device login, over-limit eviction and lockout scenarios; retained for one year.
      
revision: 966907fad0c5f0d01bc6a76ddba80bbbf67f586a
updated_at: "2026-10-03T04:41:37.316Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/audit/login-logs"
    description:
      zh: >
          写入登录、登出与失败日志。
          
      en: >
          Appends login, logout and failure entries.
          
deps:
  - kind: call
    to: oa.identity.session
    from_api: "POST /api/v1/audit/login-logs"
    label: {zh: "登录、登出与踢出事件", en: "Login/out & eviction"}
  - kind: reference
    to: oa.platform.security
    from_api: "POST /api/v1/audit/login-logs"
    label: {zh: "失败登录与锁定告警", en: "Failed-login alerting"}
  - kind: dataflow
    to: oa.identity.session.login-log
    from_api: "POST /api/v1/audit/login-logs"
    to_api: "mysql:sys_login_log"
    label: {zh: "读取登录记录", en: "Read login records"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-LOG-005`（§6.9 审计日志）
- `doc/data-model.md` → `CREATE TABLE sys_login_log`（§2. 身份与组织）
