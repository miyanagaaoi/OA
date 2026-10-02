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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.625Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
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
