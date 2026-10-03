---
uid: 51f92e01
id: oa.admin.flow.node.sign
parent: oa.admin.flow.node
state: planned
name: {zh: "签名要求", en: "Signature Policy"}
description:
  zh: >
      配置节点是强制签名、可选签名还是不签名；默认集团分管领导与集团董事长节点强制签名，其余节点可选。
      
  en: >
      Sets whether a node requires a signature, allows an optional one or forbids it, with the group executive and chairman nodes defaulting to a mandatory signature.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.248Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/flow-nodes/{node_id}/sign-policy"
    description:
      zh: >
          查询节点的签名要求。
          
      en: >
          Read the signature policy of a node.
          
  - protocol: http
    method: PUT
    path: "/api/v1/admin/flow-nodes/{node_id}/sign-policy"
    description:
      zh: >
          设置节点强制签名/可选签名/不签名。
          
      en: >
          Set required, optional or no signature for a node.
          
deps:
  - kind: call
    to: oa.sign.capture
    from_api: "PUT /api/v1/admin/flow-nodes/{node_id}/sign-policy"
    label: {zh: "签名采集执行该策略", en: "Signature capture policy"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-SIGN-003`（§6.5 电子签名与身份确认）
- `doc/data-model.md` → `CREATE TABLE flow_node`（§4. 流程定义）
