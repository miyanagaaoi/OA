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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.647Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
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
