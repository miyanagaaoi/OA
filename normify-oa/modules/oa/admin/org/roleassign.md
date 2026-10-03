---
uid: 0fd9b18d
id: oa.admin.org.roleassign
parent: oa.admin.org
state: planned
name: {zh: "角色分配", en: "Role Assignment"}
description:
  zh: >
      为人员分配角色并指定该角色生效的组织范围（scope_org_id），支持一人多角色；分配动作写入权限变更日志，分公司流程管理员不可越公司分配。
      
  en: >
      Assign roles to users together with the org scope the role applies to (scope_org_id), allowing multiple roles per user; every assignment is written to the permission-change log and branch admins cannot assign across companies.
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.293Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/users/{user_id}/roles"
    description:
      zh: >
          查询人员的角色与生效组织范围。
          
      en: >
          List the user's roles and their effective org scopes.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/users/{user_id}/roles"
    description:
      zh: >
          分配角色并指定生效组织范围。
          
      en: >
          Assign a role with an effective org scope.
          
  - protocol: http
    method: DELETE
    path: "/api/v1/admin/user-roles/{user_role_id}"
    description:
      zh: >
          解除角色分配（留痕）。
          
      en: >
          Revoke a role assignment with audit trail.
          
deps:
  - kind: call
    to: oa.authz.rbac
    from_api: "POST /api/v1/admin/users/{user_id}/roles"
    label: {zh: "角色定义来自权限域", en: "Roles come from authz"}
  - kind: event
    to: oa.admin.authz.changelog
    from_api: "POST /api/v1/admin/users/{user_id}/roles"
    label: {zh: "角色分配变更留痕", en: "Role change is logged"}
  - kind: dataflow
    to: oa.authz.rbac.user-role
    to_api: "mysql:sys_user_role"
    label: {zh: "写入用户角色关联", en: "Write user-role link"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-001`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_user_role`（§3. 权限）
