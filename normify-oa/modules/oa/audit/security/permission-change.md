---
uid: "11361646"
id: oa.audit.security.permission-change
parent: oa.audit.security
state: planned
name: {zh: "权限变更日志", en: "Permission-Change Log"}
description:
  zh: >
      角色、数据域、权限树勾选与流程模板发布的变更全部留痕，强制记录操作人、时间与变更前后值，满足权限变更的可追溯要求。
      
  en: >
      Logs every change to roles, data scopes, permission-tree selections and flow template publications, always capturing actor, timestamp and before/after values for permission-change traceability.
      
revision: 112ab0a1d46779714029044fc8e0b46627804f30
updated_at: "2026-10-03T01:51:21.549Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 429
    end_line: 429
  - path: "doc/prd-0.1.md"
    line: 167
    end_line: 167
apis:
  - protocol: http
    method: POST
    path: "/api/v1/audit/permission-changes"
    description:
      zh: >
          记录一条权限变更（含前后值）。
          
      en: >
          Records one permission change with before/after values.
          
  - protocol: http
    method: GET
    path: "/api/v1/audit/permission-changes"
    description:
      zh: >
          按时间与对象检索权限变更日志。
          
      en: >
          Lists permission-change log entries.
          
deps:
  - kind: event
    to: oa.authz.rbac
    from_api: "POST /api/v1/audit/permission-changes"
    label: {zh: "角色与权限树勾选变更留痕", en: "Role and permission changes"}
  - kind: event
    to: oa.authz.scope
    from_api: "POST /api/v1/audit/permission-changes"
    label: {zh: "数据域变更留痕", en: "Data scope changes"}
  - kind: event
    to: oa.admin.flow
    from_api: "POST /api/v1/audit/permission-changes"
    label: {zh: "流程模板发布留痕", en: "Flow template publication"}
---
