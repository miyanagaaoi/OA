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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.735Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
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
