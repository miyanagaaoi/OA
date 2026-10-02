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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.696Z"
fingerprint: 1ebf4aee4ac8648bfaec0ea0afbfecc1b3e2f5fecc0dff532be914d9e85be0f2
source:
  - path: "doc/prd-0.1.md"
    line: 184
    end_line: 190
  - path: "doc/data-model.md"
    line: 722
    end_line: 735
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
