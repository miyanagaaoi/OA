---
uid: 2ec08c82
id: oa.workflow.routing.rollback
parent: oa.workflow.routing
state: planned
name: {zh: "回退上一节点", en: "Roll Back to Previous Node"}
description:
  zh: >
      把单据退回上一个已完成节点重审：必须填写原因；当前节点实例置 returned，上一节点实例回到 active 且 returned_count +1，上一节点通过后自动回到本节点。同一节点最多被回退 2 次，每次计入总闸门（流转+回退 ≤5）。
      
  en: >
      Roll the document back to the previous completed node for re-approval: a reason is mandatory; the current node instance becomes returned, the previous node instance returns to active with returned_count +1, and once it passes the document automatically comes back. A node may be rolled back at most twice and each rollback counts towards the total gate.
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.444Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/prd-0.1.md"
    line: 323
    end_line: 323
  - path: "doc/prd-0.1.md"
    line: 358
    end_line: 358
  - path: "doc/data-model.md"
    line: 435
    end_line: 435
  - path: "doc/prd-0.1.md"
    line: 505
    end_line: 505
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/instances/{instance_id}/rollback"
    description:
      zh: >
          回退上一已完成节点（原因必填、同节点 ≤2 次）。
          
      en: >
          Roll back to the previous completed node (reason required, max twice).
          
  - protocol: http
    method: POST
    path: "/api/v1/flow/node-instances/{node_instance_id}/return-resume"
    description:
      zh: >
          上一节点通过后自动回到被回退的节点。
          
      en: >
          Return to the rolled-back node once the previous node passes again.
          
deps:
  - kind: call
    to: oa.workflow.runtime
    from_api: "POST /api/v1/flow/instances/{instance_id}/rollback"
    label: {zh: "节点置已退回/进行中", en: "Set returned node state"}
  - kind: call
    to: oa.workflow.routing.gate.quota
    from_api: "POST /api/v1/flow/instances/{instance_id}/rollback"
    to_api: "POST /api/v1/flow/instances/{instance_id}/routing-quota/check"
    label: {zh: "回退前过次数闸门", en: "Enforce routing quota gate"}
---
