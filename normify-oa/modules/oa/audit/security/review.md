---
uid: 1641183e
id: oa.audit.security.review
parent: oa.audit.security
state: planned
name: {zh: "安全日志审计视图", en: "Security Log Review"}
description:
  zh: >
      权限变更日志与登录日志的联合检索与失败登录、异常 IP 分析，按数据域限制可见范围，并支持安全日志导出供审计。
      
  en: >
      Joint query across permission-change and login logs with failed-login and anomalous-IP analysis, scoped by data domain, plus export of security logs for audit.
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.687Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 429
    end_line: 430
  - path: "doc/prd-0.1.md"
    line: 440
    end_line: 440
apis:
  - protocol: http
    method: GET
    path: "/api/v1/audit/security-logs"
    description:
      zh: >
          联合检索权限变更与登录日志。
          
      en: >
          Unified query over permission-change and login logs.
          
  - protocol: http
    method: GET
    path: "/api/v1/audit/security-logs/failed-attempts"
    description:
      zh: >
          失败登录与异常来源分析。
          
      en: >
          Failed-login and anomalous-source analysis.
          
  - protocol: file
    path: "export/audit/security-log/{yyyy}/{mm}.csv"
    description:
      zh: >
          按月导出的安全日志审计文件。
          
      en: >
          Monthly CSV export of security logs.
          
deps:
  - kind: call
    to: oa.authz.visibility
    from_api: "GET /api/v1/audit/security-logs"
    label: {zh: "按数据域过滤可见范围", en: "Filter by data scope"}
  - kind: reference
    to: oa.admin.boundary
    from_api: "GET /api/v1/audit/security-logs"
    label: {zh: "复核管理员操作留痕", en: "Review admin traces"}
---
