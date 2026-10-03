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
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.534Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
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
