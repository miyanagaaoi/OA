---
uid: 3d49c802
id: oa.admin.flow.node
parent: oa.admin.flow
state: planned
name: {zh: "节点规则配置", en: "Node Rule Config"}
description:
  zh: >
      逐节点规则配置：决议模式与通过阈值、签名要求、超时时长，以及加签/跳转等流转开关。
      
  en: >
      Per-node rule configuration: decision mode and pass threshold, signature policy, timeout length and routing gates such as add-sign or jump.
      
revision: 44fc7aba1c7e884ffa3553faf31bfce974b388a9
updated_at: "2026-10-03T04:12:20.648Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-002`（§6.4 流程引擎核心能力）
- `doc/data-model.md` → `CREATE TABLE flow_node`（§4. 流程定义）
