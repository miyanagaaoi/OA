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
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.122Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-002`（§6.4 流程引擎核心能力）
- `doc/data-model.md` → `CREATE TABLE flow_node`（§4. 流程定义）
