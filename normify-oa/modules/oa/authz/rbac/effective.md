---
uid: 5008f6ed
id: oa.authz.rbac.effective
parent: oa.authz.rbac
name: {zh: "有效权限计算", en: "Effective Permissions"}
description:
  zh: >
      汇总用户全部角色的权限码并集与组织范围，生成菜单/按钮视图并缓存；角色、授权或用户角色变更后按用户失效缓存。
      
  en: >
      Merges the permission codes and org scopes of all of a user's roles, produces the menu/button view and caches it; caches are invalidated per user when roles or grants change.
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.286Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-AUTH-001`（§5.2 权限模型）
- `doc/data-model.md` → `CREATE TABLE sys_role_permission`（§3. 权限）
