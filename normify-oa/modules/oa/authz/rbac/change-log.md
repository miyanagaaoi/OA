---
uid: 50dd20e0
id: oa.authz.rbac.change-log
parent: oa.authz.rbac
name: {zh: "权限变更留痕", en: "Permission Change Log"}
description:
  zh: >
      角色、数据域、权限树勾选与组织授权范围的任何变更都记录操作人、时间与变更前后值，并推送到审计日志（REQ-LOG-004 的产生侧）。
      
  en: >
      Every change to roles, data scopes, permission-tree ticks and org grants records actor, time and before/after values and is pushed to the audit log (producer side of REQ-LOG-004).
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.650Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 159
    end_line: 168
  - path: "doc/prd-0.1.md"
    line: 422
    end_line: 431
apis:
  - protocol: http
    method: GET
    path: "/api/v1/authz/change-logs"
    description:
      zh: >
          查询权限变更记录（含前后值）。
          
      en: >
          Queries permission change records with before/after values.
          
  - protocol: kafka
    path: "oa.authz.permission.changed"
    description:
      zh: >
          权限变更事件主题。
          
      en: >
          Permission-change event topic.
          
deps:
  - kind: event
    to: oa.audit.oplog
    label: {zh: "变更前后值写入审计日志", en: "Push permission changes"}
---
