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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.627Z"
fingerprint: 4421092e639ef98e9cd4aac53b5975054268b604256d140e3544a8a3c2ba5551
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
