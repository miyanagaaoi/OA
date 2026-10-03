---
uid: "67447538"
id: oa.admin.authz.tree.assign
parent: oa.admin.authz.tree
state: planned
name: {zh: "角色勾选权限", en: "Role Permission Ticking"}
description:
  zh: >
      读写某角色被勾选的权限树节点集合，支持逐级联动与批量勾选，并把勾选差异交给权限变更日志。
      
  en: >
      Reads and saves the set of permission-tree nodes ticked for a role, supporting node-by-node cascading and bulk ticking, with the resulting delta handed to the permission-change log.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.110Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/roles/{role_id}/permissions"
    description:
      zh: >
          查询角色已勾选的权限树节点。
          
      en: >
          List the permission-tree nodes already ticked for a role.
          
  - protocol: http
    method: PUT
    path: "/api/v1/admin/roles/{role_id}/permissions"
    description:
      zh: >
          保存角色的权限树勾选结果。
          
      en: >
          Save the ticked permission nodes of a role.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/roles/{role_id}/permissions/batch"
    description:
      zh: >
          批量勾选/取消权限节点。
          
      en: >
          Tick or untick many nodes in one call.
          
deps:
  - kind: call
    to: oa.authz.rbac
    from_api: "PUT /api/v1/admin/roles/{role_id}/permissions"
    label: {zh: "权限树节点来自权限域", en: "Permission nodes from authz"}
  - kind: event
    to: oa.admin.authz.changelog
    from_api: "PUT /api/v1/admin/roles/{role_id}/permissions"
    label: {zh: "勾选变更留痕", en: "Tick change is logged"}
  - kind: dataflow
    to: oa.authz.rbac.grant.menu
    to_api: "mysql:sys_role_permission"
    label: {zh: "写入角色权限", en: "Write role permission"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-003`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_role_permission`（§3. 权限）
