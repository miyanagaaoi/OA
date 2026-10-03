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
      
revision: 966907fad0c5f0d01bc6a76ddba80bbbf67f586a
updated_at: "2026-10-03T04:41:37.368Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `### 8.1 接口设计原则`（§8.1 接口设计原则）
