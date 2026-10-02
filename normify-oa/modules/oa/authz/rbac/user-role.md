---
uid: "426e6923"
id: oa.authz.rbac.user-role
parent: oa.authz.rbac
state: planned
name: {zh: "用户角色分配", en: "User-role Assignment"}
description:
  zh: >
      把角色授予用户，可限定该角色生效的组织范围（scope_org_id，为空则取角色默认）；分公司流程管理员只能在本公司范围内分配，且不可再向下授权。
      
  en: >
      Grants roles to users, optionally bounded by the org scope where the role takes effect (scope_org_id, falling back to the role default); branch process admins may assign only inside their own company and cannot delegate further.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.632Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
source:
  - path: "doc/data-model.md"
    line: 188
    end_line: 203
  - path: "doc/prd-0.1.md"
    line: 159
    end_line: 168
apis:
  - protocol: http
    method: GET
    path: "/api/v1/authz/users/{id}/roles"
    description:
      zh: >
          查询用户的角色与生效范围。
          
      en: >
          Lists a user's roles and their scope.
          
  - protocol: http
    method: POST
    path: "/api/v1/authz/users/{id}/roles"
    description:
      zh: >
          授予角色（可限定组织范围）。
          
      en: >
          Grants a role with an optional org scope.
          
  - protocol: http
    method: DELETE
    path: "/api/v1/authz/users/{id}/roles/{assignmentId}"
    description:
      zh: >
          回收角色授权。
          
      en: >
          Revokes a role assignment.
          
  - protocol: mysql
    path: "sys_user_role"
    description:
      zh: >
          用户角色关联表。
          
      en: >
          User-role table.
          
deps:
  - kind: call
    to: oa.authz.rbac.role
    from_api: "POST /api/v1/authz/users/{id}/roles"
    to_api: "GET /api/v1/authz/roles"
    label: {zh: "校验角色存在", en: "Validate role"}
  - kind: call
    to: oa.identity.user.profile
    from_api: "POST /api/v1/authz/users/{id}/roles"
    to_api: "GET /api/v1/identity/users"
    label: {zh: "校验被授权人", en: "Validate grantee"}
---
