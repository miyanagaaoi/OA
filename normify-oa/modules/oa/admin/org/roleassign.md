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
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.127Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
    line: 435
    end_line: 435
  - path: "doc/prd-0.1.md"
    line: 166
    end_line: 166
  - path: "doc/data-model.md"
    line: 191
    end_line: 203
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
