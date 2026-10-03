---
uid: 2d3d5e44
id: oa.workflow.definition
parent: oa.workflow
state: planned
name: {zh: "流程模板与节点定义", en: "Templates & Node Definitions"}
description:
  zh: >
      流程模板与节点定义：序号、节点类型（审批/条件/抄送/归档）、审批人解析规则、决议模式、通过阈值、签名策略、超时时长、是否允许加签/跳转；模板变更生成新版本，已发起实例按发起时版本与快照执行。
      
  en: >
      Process templates and node definitions: sequence, node type (approval/condition/CC/archive), approver rule, decision mode, pass threshold, signature policy, timeout hours, add-sign and jump switches; template changes create versions and in-flight instances stay on their original version.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.307Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-006`（§6.4 流程引擎核心能力）
- `doc/data-model.md` → `CREATE TABLE flow_node`（§4. 流程定义）
