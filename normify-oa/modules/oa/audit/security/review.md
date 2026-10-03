---
uid: 1641183e
id: oa.audit.security.review
parent: oa.audit.security
name: {zh: "安全日志审计视图", en: "Security Log Review"}
description:
  zh: >
      权限变更日志与登录日志的联合检索与失败登录、异常 IP 分析，按数据域限制可见范围，并支持安全日志导出供审计。
      
  en: >
      Joint query across permission-change and login logs with failed-login and anomalous-IP analysis, scoped by data domain, plus export of security logs for audit.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.405Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-LOG-005`（§6.9 审计日志）
