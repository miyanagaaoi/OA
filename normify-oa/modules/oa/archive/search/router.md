---
uid: 353af95b
id: oa.archive.search.router
parent: oa.archive.search
state: planned
name: {zh: "检索路由", en: "Archive Search Router"}
description:
  zh: >
      统一历史检索入口，按单据年代与归档标记在在线库与历史库之间路由查询，返回数据来源标识与只读态。
      
  en: >
      Single archive search entry that routes a query to the live store or the history store by document age and archive flag, returning the data source and read-only state.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.266Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/archive/search"
    description:
      zh: >
          统一历史检索入口。
          
      en: >
          Unified archive search entry with store routing.
          
  - protocol: http
    method: GET
    path: "/api/v1/archive/search/stores"
    description:
      zh: >
          查看在线库与历史库状态。
          
      en: >
          Reports live and history store status.
          
deps:
  - kind: call
    to: oa.archive.policy.readonly
    from_api: "GET /api/v1/archive/search"
    to_api: "GET /api/v1/archive/instances/{biz_no}/capabilities"
    label: {zh: "读取只读态与能力位", en: "Read read-only capabilities"}
  - kind: call
    to: oa.authz.visibility
    from_api: "GET /api/v1/archive/search"
    label: {zh: "按数据域过滤", en: "Filter by data scope"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-NFR-010`（§第9章 非功能需求）
- `doc/data-model.md` → `## 10. 归档策略（对应 REQ-NFR-010）`（§10. 归档策略）
