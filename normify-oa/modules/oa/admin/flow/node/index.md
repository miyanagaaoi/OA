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
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.286Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-002`（§6.4 流程引擎核心能力）
- `doc/data-model.md` → `CREATE TABLE flow_node`（§4. 流程定义）
