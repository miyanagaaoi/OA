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
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.243Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/prd-0.1.md"
    line: 169
    end_line: 183
  - path: "doc/data-model.md"
    line: 722
    end_line: 733
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
