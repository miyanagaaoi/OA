---
uid: 51f92e01
id: oa.admin.flow.node.sign
parent: oa.admin.flow.node
name: {zh: "签名要求", en: "Signature Policy"}
description:
  zh: >
      配置节点是强制签名、可选签名还是不签名；默认集团分管领导与集团董事长节点强制签名，其余节点可选。
      
  en: >
      Sets whether a node requires a signature, allows an optional one or forbids it, with the group executive and chairman nodes defaulting to a mandatory signature.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:39.884Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
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
