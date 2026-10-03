---
uid: 038b1665
id: oa.identity.org.state
parent: oa.identity.org
name: {zh: "停用与在途闸门", en: "Disable Gate & In-flight Check"}
description:
  zh: >
      组织节点停用/启用：停用前必须处理完该节点全部在途单据，停用后不可作为发起者归属节点；闸门以受影响在途单据清单形式返回并阻断操作。
      
  en: >
      Enable/disable for org nodes: disabling requires all in-flight documents of the node to be finished first, and a disabled node can no longer be an initiator's org; the gate returns the affected in-flight list and blocks the action.
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.316Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 234
    end_line: 244
apis:
  - protocol: http
    method: POST
    path: "/api/v1/identity/orgs/{id}/disable"
    description:
      zh: >
          停用节点（在途校验通过后）。
          
      en: >
          Disables a node after the in-flight check passes.
          
  - protocol: http
    method: POST
    path: "/api/v1/identity/orgs/{id}/enable"
    description:
      zh: >
          启用节点。
          
      en: >
          Enables a node.
          
  - protocol: http
    method: GET
    path: "/api/v1/identity/orgs/{id}/in-flight-check"
    description:
      zh: >
          列出该节点在途单据数量与清单。
          
      en: >
          Lists in-flight documents of the node.
          
deps:
  - kind: call
    to: oa.identity.org.node
    from_api: "POST /api/v1/identity/orgs/{id}/disable"
    to_api: "PUT /api/v1/identity/orgs/{id}"
    label: {zh: "回写启用/停用状态", en: "Write back status"}
  - kind: dataflow
    to: oa.workflow.runtime
    label: {zh: "停用前核对在途单据", en: "Check in-flight docs"}
---
