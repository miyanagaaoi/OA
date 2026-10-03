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
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.121Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
    line: 386
    end_line: 386
  - path: "doc/prd-0.1.md"
    line: 556
    end_line: 556
  - path: "doc/data-model.md"
    line: 311
    end_line: 311
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
