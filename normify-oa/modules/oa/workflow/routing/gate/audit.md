---
uid: 3accf908
id: oa.workflow.routing.gate.audit
parent: oa.workflow.routing.gate
state: planned
name: {zh: "流转动作留痕", en: "Routing Action Trail"}
description:
  zh: >
      所有流转、回退、回到本部门与补件动作统一写入审计日志与审批轨迹：记录操作人、动作类型、来源/目标部门、原因与时间；审计记录只追加不可改，供统计与责任认定。
      
  en: >
      Every routing, rollback, return-home and supplement action is written uniformly to the audit log and the approval trail: actor, action type, source and target department, reason and timestamp. Audit rows are append-only and support statistics and accountability.
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.688Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: kafka
    path: "oa.workflow.routing.action-logged"
    description:
      zh: >
          流转/回退/补件动作事件（供审计与轨迹落地）。
          
      en: >
          Event for every route, rollback and supplement action.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow/instances/{instance_id}/routing-actions"
    description:
      zh: >
          流转链动作流水（含原因与操作人）。
          
      en: >
          Routing action stream with reason and actor.
          
deps:
  - kind: call
    to: oa.audit.oplog
    from_api: "kafka:oa.workflow.routing.action-logged"
    label: {zh: "写操作日志", en: "Write the operation log"}
  - kind: call
    to: oa.audit.trace
    from_api: "kafka:oa.workflow.routing.action-logged"
    label: {zh: "写审批轨迹", en: "Write the approval trail"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-024`（§6.4 流程引擎核心能力）
- `doc/data-model.md` → `CREATE TABLE flow_routing`（§5. 流程运行时）
