---
uid: 76a74dbb
id: oa.authz.visibility.routing
parent: oa.authz.visibility
state: planned
name: {zh: "流转链可见性", en: "Routing-chain Visibility"}
description:
  zh: >
      被指定为流转目标部门的负责人对该张在途单据可见（即使类别不属其归口）；可见性仅限该单据、不得折算为类别可见，单据完结后保留历史可查。
      
  en: >
      A department named as a routing target may see that specific in-flight document even when the category is outside its remit; the visibility is per-document and must never widen into category-level access, and it persists read-only after completion.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.170Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: rpc
    path: "authz.visibility.routingCheck"
    description:
      zh: >
          判定用户是否因流转链对该单据可见。
          
      en: >
          Checks visibility via the routing chain.
          
  - protocol: http
    method: GET
    path: "/api/v1/authz/routing-visibility/{instanceId}"
    description:
      zh: >
          读取某单据的流转链可见部门。
          
      en: >
          Lists departments visible on a document's routing chain.
          
deps:
  - kind: call
    to: oa.authz.scope.filter
    from_api: "rpc:authz.visibility.routingCheck"
    to_api: "rpc:authz.scope.buildFilter"
    label: {zh: "并入流转可见性条件", en: "Add routing visibility"}
  - kind: dataflow
    to: oa.workflow.runtime
    label: {zh: "读取流转链记录", en: "Read routing chain"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-AUTH-004`（§5.3 数据域口径）
- `doc/data-model.md` → `CREATE TABLE flow_routing`（§5. 流程运行时）
