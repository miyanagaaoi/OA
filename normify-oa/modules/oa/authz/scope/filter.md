---
uid: 59239a61
id: oa.authz.scope.filter
parent: oa.authz.scope
name: {zh: "查询过滤构建器", en: "Scope Filter Builder"}
description:
  zh: >
      把数据域口径编译成查询过滤条件：发起人本人或任务 assignee/抄送人、组织路径前缀、公司、无过滤；供单据列表、搜索与详情鉴权复用。
      
  en: >
      Compiles a scope into query filters: initiator or task assignee or CC user, org path prefix, company, or no filter at all; reused by document lists, search and detail authorisation.
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.241Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/data-model.md"
    line: 722
    end_line: 733
  - path: "doc/prd-0.1.md"
    line: 169
    end_line: 183
apis:
  - protocol: rpc
    path: "authz.scope.buildFilter"
    description:
      zh: >
          生成指定用户的数据域过滤条件。
          
      en: >
          Builds the scope filter of a user.
          
  - protocol: http
    method: POST
    path: "/api/v1/authz/scope-preview"
    description:
      zh: >
          预览某角色的可见范围（后台配置用）。
          
      en: >
          Previews the visible range of a role.
          
deps:
  - kind: call
    to: oa.authz.scope.resolve
    from_api: "rpc:authz.scope.buildFilter"
    to_api: "rpc:authz.scope.resolve"
    label: {zh: "先解析数据域口径", en: "Resolve scope first"}
  - kind: call
    to: oa.authz.scope.category
    from_api: "rpc:authz.scope.buildFilter"
    to_api: "GET /api/v1/authz/categories"
    label: {zh: "归口类别口径输入", en: "Category scope input"}
---
