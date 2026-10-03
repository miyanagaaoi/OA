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
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.158Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-LOG-004`（§6.9 审计日志）
