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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.619Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
source:
  - path: "doc/prd-0.1.md"
    line: 540
    end_line: 540
  - path: "doc/data-model.md"
    line: 824
    end_line: 824
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
