---
uid: 50dd20e0
id: oa.authz.rbac.change-log
parent: oa.authz.rbac
state: planned
name: {zh: "权限变更留痕", en: "Permission Change Log"}
description:
  zh: >
      角色、数据域、权限树勾选与组织授权范围的任何变更都记录操作人、时间与变更前后值，并推送到审计日志（REQ-LOG-004 的产生侧）。
      
  en: >
      Every change to roles, data scopes, permission-tree ticks and org grants records actor, time and before/after values and is pushed to the audit log (producer side of REQ-LOG-004).
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.628Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
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
