---
uid: 23c2ed81
id: oa.workflow.task.addsign.chain
parent: oa.workflow.task.addsign
state: planned
name: {zh: "加签链与留痕", en: "Add-sign Chain & Trail"}
description:
  zh: >
      维护节点实例上的加签链记录（add_sign_chain_json）：记录加签类型（前加签/后加签）、加签人、插入位置与时间；加签行为写入审计日志与审批轨迹，供后续责任认定与页面展示。
      
  en: >
      Maintains the add-sign chain on the node instance: the type (before/after), the added signer, insertion position and time. Every add-sign action is written to the audit log and the approval trail for later accountability.
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.459Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/data-model.md"
    line: 437
    end_line: 437
  - path: "doc/prd-0.1.md"
    line: 392
    end_line: 392
apis:
  - protocol: http
    method: GET
    path: "/api/v1/flow/node-instances/{node_instance_id}/add-sign-chain"
    description:
      zh: >
          读取节点加签链（前后加签顺序与加签人）。
          
      en: >
          Read the node add-sign chain with signers and order.
          
  - protocol: kafka
    path: "oa.workflow.task.add-signed"
    description:
      zh: >
          加签动作事件（写审计日志与轨迹）。
          
      en: >
          Event emitted on every add-sign action.
          
deps:
  - kind: call
    to: oa.audit.oplog
    from_api: "kafka:oa.workflow.task.add-signed"
    label: {zh: "加签行为写审计日志", en: "Audit the add-sign action"}
  - kind: call
    to: oa.audit.trace
    from_api: "kafka:oa.workflow.task.add-signed"
    label: {zh: "加签链记入审批轨迹", en: "Record chain in trail"}
---
