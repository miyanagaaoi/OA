---
uid: 5008f6ed
id: oa.authz.rbac.effective
parent: oa.authz.rbac
state: planned
name: {zh: "有效权限计算", en: "Effective Permissions"}
description:
  zh: >
      汇总用户全部角色的权限码并集与组织范围，生成菜单/按钮视图并缓存；角色、授权或用户角色变更后按用户失效缓存。
      
  en: >
      Merges the permission codes and org scopes of all of a user's roles, produces the menu/button view and caches it; caches are invalidated per user when roles or grants change.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.629Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
source:
  - path: "doc/prd-0.1.md"
    line: 159
    end_line: 168
  - path: "doc/data-model.md"
    line: 238
    end_line: 251
apis:
  - protocol: http
    method: GET
    path: "/api/v1/authz/effective-permissions"
    description:
      zh: >
          返回当前用户的有效权限码与菜单。
          
      en: >
          Returns the caller's effective codes and menus.
          
  - protocol: http
    method: POST
    path: "/api/v1/authz/cache/invalidate"
    description:
      zh: >
          按用户或角色失效权限缓存。
          
      en: >
          Invalidates the permission cache by user or role.
          
  - protocol: redis
    path: "authz:perms:{userId}"
    description:
      zh: >
          用户有效权限缓存键。
          
      en: >
          Effective-permission cache key.
          
deps:
  - kind: call
    to: oa.authz.rbac.user-role
    from_api: "GET /api/v1/authz/effective-permissions"
    to_api: "GET /api/v1/authz/users/{id}/roles"
    label: {zh: "读取用户角色", en: "Read user roles"}
  - kind: call
    to: oa.authz.rbac.grant.menu
    from_api: "GET /api/v1/authz/effective-permissions"
    to_api: "GET /api/v1/authz/roles/{id}/permissions"
    label: {zh: "读取角色已授予权限码", en: "Read granted codes"}
  - kind: call
    to: oa.authz.rbac.permission-tree
    from_api: "GET /api/v1/authz/effective-permissions"
    to_api: "GET /api/v1/authz/permissions/tree"
    label: {zh: "按权限树装配菜单", en: "Read permission tree"}
---
