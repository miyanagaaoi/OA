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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.646Z"
fingerprint: 4421092e639ef98e9cd4aac53b5975054268b604256d140e3544a8a3c2ba5551
source:
  - path: "doc/prd-0.1.md"
    line: 430
    end_line: 430
  - path: "doc/data-model.md"
    line: 841
    end_line: 841
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
