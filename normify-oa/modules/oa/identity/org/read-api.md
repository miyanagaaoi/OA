---
uid: 06a791ed
id: oa.identity.org.read-api
parent: oa.identity.org
state: planned
name: {zh: "组织只读查询接口", en: "Org Read-only Query API"}
description:
  zh: >
      内部组织只读查询接口（列表与详情），供门户与集成面复用；一期对外只开放只读查询、不提供任何写入接口，避免权限模型成熟前引入外部写入风险（对外的 open 前缀由集成分支暴露）。
      
  en: >
      Internal read-only org queries (list and detail) reused by the portal and the integration surface; phase one publishes read-only access only, with no write endpoints before the permission model matures (the public open prefix belongs to the integration branch).
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.703Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 509
    end_line: 524
apis:
  - protocol: http
    method: GET
    path: "/api/v1/orgs"
    description:
      zh: >
          内部只读组织列表。
          
      en: >
          Internal read-only org list.
          
  - protocol: http
    method: GET
    path: "/api/v1/orgs/{id}"
    description:
      zh: >
          内部只读组织详情。
          
      en: >
          Internal read-only org detail.
          
deps:
  - kind: call
    to: oa.identity.org.node
    from_api: "GET /api/v1/orgs"
    to_api: "GET /api/v1/identity/orgs/tree"
    label: {zh: "由内部组织树装配只读视图", en: "Build read-only view"}
---
