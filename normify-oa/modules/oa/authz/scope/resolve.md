---
uid: 565594eb
id: oa.authz.scope.resolve
parent: oa.authz.scope
state: planned
name: {zh: "用户数据域解析", en: "Data Scope Resolution"}
description:
  zh: >
      解析用户的最终数据域：合并多角色取最宽口径，叠加角色生效组织范围（scope_org_id）与一人多岗归属，输出可直接用于过滤的口径对象。
      
  en: >
      Resolves a user's final data scope by merging roles with the widest-wins rule, layering each role's effective org scope (scope_org_id) and multi-post affiliations into a filter-ready scope object.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.636Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
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
