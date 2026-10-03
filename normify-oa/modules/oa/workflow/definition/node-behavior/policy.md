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
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.597Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-SIGN-003`（§6.5 电子签名与身份确认）
