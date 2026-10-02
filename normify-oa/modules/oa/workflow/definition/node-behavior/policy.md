---
uid: 0fddf872
id: oa.workflow.definition.node-behavior.policy
parent: oa.workflow.definition.node-behavior
state: planned
name: {zh: "签名策略与超时开关", en: "Signature Policy & Timeout Switches"}
description:
  zh: >
      节点级 sign_policy（required 强制 / optional 可选 / none 不签名，默认集团分管领导与董事长节点强制）、timeout_hours（须显式配置且 ≥24h，超时仅催办不自动跳过）、allow_add_sign（加签）、allow_jump（自由跳转默认关闭）、allow_route（集团层流转/回退开关）（REQ-FLOW-007/003/004/020、REQ-SIGN-003）。
      
  en: >
      Node-level sign_policy (required/optional/none; group line leader and chairman default to required), timeout_hours (explicit and ≥24h, reminder only and never auto-skip), allow_add_sign, allow_jump (off by default) and allow_route for group-layer routing/return (REQ-FLOW-007/003/004/020, REQ-SIGN-003).
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.768Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 346
    end_line: 352
  - path: "doc/prd-0.1.md"
    line: 367
    end_line: 367
  - path: "doc/prd-0.1.md"
    line: 550
    end_line: 556
apis:
  - protocol: http
    method: GET
    path: "/api/v1/flow-nodes/{node_id}/policy"
    description:
      zh: >
          读取签名策略、超时时长与加签/跳转/流转开关。
          
      en: >
          Reads signature policy, timeout hours and the add-sign/jump/route switches.
          
  - protocol: http
    method: PUT
    path: "/api/v1/flow-nodes/{node_id}/policy"
    description:
      zh: >
          写入签名策略、超时时长与三个开关。
          
      en: >
          Writes signature policy, timeout hours and the three switches.
          
  - protocol: http
    method: POST
    path: "/api/v1/flow-nodes/{node_id}/policy/validate"
    description:
      zh: >
          校验超时 ≥24h、强制签名默认值与开关适用性。
          
      en: >
          Validates timeout ≥24h, the mandatory-signature defaults and switch applicability.
          
deps:
  - kind: reference
    to: oa.workflow.definition.node-schema
    from_api: "PUT /api/v1/flow-nodes/{node_id}/policy"
    to_api: "PUT /api/v1/flow-nodes/{node_id}"
    label: {zh: "策略字段属于节点", en: "Policy fields on node"}
  - kind: reference
    to: oa.sign.preset
    from_api: "POST /api/v1/flow-nodes/{node_id}/policy/validate"
    label: {zh: "强制签名用预存签名", en: "Stored signature presets"}
  - kind: reference
    to: oa.notify.reminder
    from_api: "PUT /api/v1/flow-nodes/{node_id}/policy"
    label: {zh: "超时时长决定催办", en: "Timeout drives reminders"}
---
