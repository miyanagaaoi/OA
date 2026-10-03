---
uid: 0795c60e
id: oa.audit.oplog.query
parent: oa.audit.oplog
name: {zh: "操作日志检索与导出", en: "Operation Log Query"}
description:
  zh: >
      按操作人、时间区间、目标对象与动作类型组合检索操作日志，提供单条详情与审计导出（CSV）；导出范围与导出行为本身同样留痕。
      
  en: >
      Queries the operation log by actor, time range, target and action, with entry detail and CSV audit export; export scope and the export action itself are logged.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:39.944Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/audit/logs"
    description:
      zh: >
          分页检索操作日志。
          
      en: >
          Lists operation log entries with filters.
          
  - protocol: http
    method: GET
    path: "/api/v1/audit/logs/{id}"
    description:
      zh: >
          查看单条操作日志详情。
          
      en: >
          Returns one operation log entry.
          
  - protocol: file
    path: "export/audit/operation-log/{yyyy}/{mm}.csv"
    description:
      zh: >
          按月导出的操作日志审计文件。
          
      en: >
          Monthly CSV export of the operation log for audit.
          
deps:
  - kind: call
    to: oa.authz.visibility
    from_api: "GET /api/v1/audit/logs"
    label: {zh: "按数据域过滤可见范围", en: "Filter by data scope"}
  - kind: reference
    to: oa.portal.detail
    from_api: "GET /api/v1/audit/logs"
    label: {zh: "详情页展示相关操作日志", en: "Show entries on detail page"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-LOG-001`（§6.9 审计日志）
- `doc/data-model.md` → `CREATE TABLE sys_log`（§6. 签名、附件、抄送、消息、审计）
