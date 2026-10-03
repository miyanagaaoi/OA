---
uid: 565594eb
id: oa.authz.scope.resolve
parent: oa.authz.scope
name: {zh: "用户数据域解析", en: "Data Scope Resolution"}
description:
  zh: >
      解析用户的最终数据域：合并多角色取最宽口径，叠加角色生效组织范围（scope_org_id）与一人多岗归属，输出可直接用于过滤的口径对象。
      
  en: >
      Resolves a user's final data scope by merging roles with the widest-wins rule, layering each role's effective org scope (scope_org_id) and multi-post affiliations into a filter-ready scope object.
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.322Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: rpc
    path: "authz.scope.resolve"
    description:
      zh: >
          解析指定用户的数据域口径对象。
          
      en: >
          Resolves the scope object of a user.
          
  - protocol: http
    method: GET
    path: "/api/v1/authz/my-scope"
    description:
      zh: >
          返回当前登录人的数据域。
          
      en: >
          Returns the caller's data scope.
          
deps:
  - kind: call
    to: oa.authz.scope.catalog
    from_api: "rpc:authz.scope.resolve"
    to_api: "GET /api/v1/authz/data-scopes"
    label: {zh: "读取口径取值", en: "Read scope catalog"}
  - kind: call
    to: oa.authz.rbac.effective
    from_api: "rpc:authz.scope.resolve"
    to_api: "GET /api/v1/authz/effective-permissions"
    label: {zh: "合并角色数据域", en: "Merge role scopes"}
  - kind: call
    to: oa.identity.position.multi-post
    from_api: "rpc:authz.scope.resolve"
    to_api: "GET /api/v1/identity/users/{id}/positions"
    label: {zh: "一人多岗取最宽口径", en: "Widest scope from posts"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-AUTH-001`（§5.2 权限模型）
- `doc/data-model.md` → `CREATE TABLE sys_role`（§3. 权限）
