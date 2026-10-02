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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.777Z"
fingerprint: 4421092e639ef98e9cd4aac53b5975054268b604256d140e3544a8a3c2ba5551
source:
  - path: "doc/prd-0.1.md"
    line: 361
    end_line: 361
  - path: "doc/prd-0.1.md"
    line: 322
    end_line: 322
  - path: "doc/data-model.md"
    line: 488
    end_line: 488
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
