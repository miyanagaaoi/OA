---
uid: 36b33fde
id: oa.workflow.runtime.instance.state
parent: oa.workflow.runtime.instance
state: planned
name: {zh: "实例状态与迁移", en: "Instance Status & Transitions"}
description:
  zh: >
      实例状态读写与受保护迁移：状态枚举 draft/approving/approved/rejected/withdrawn/terminated 与子状态 supplement 的校验落库（仅审批中可带子状态）；迁移后广播状态变更事件供通知与审计消费。
      
  en: >
      Reads and writes instance status with guarded transitions: persists the draft/approving/approved/rejected/withdrawn/terminated enum plus the supplement sub-status (only approving may carry it) and broadcasts a status-changed event for notification and audit consumers.
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.312Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
    line: 475
    end_line: 486
  - path: "doc/data-model.md"
    line: 452
    end_line: 494
apis:
  - protocol: mysql
    path: "flow_instance"
    description:
      zh: >
          流程实例表：状态、子状态与运行计数。
          
      en: >
          Flow instance table: status, sub_status and runtime counters.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow-instances/{instance_id}/state"
    description:
      zh: >
          读取实例状态、子状态与计数。
          
      en: >
          Reads instance status, sub-status and counters.
          
  - protocol: http
    method: POST
    path: "/api/v1/flow-instances/{instance_id}/transitions"
    description:
      zh: >
          执行受保护的状态迁移（提交/撤回/重提/终止/决议结果）。
          
      en: >
          Applies a guarded status transition (submit/withdraw/resubmit/terminate/decide).
          
  - protocol: kafka
    path: "oa.workflow.instance.status-changed"
    description:
      zh: >
          状态变更事件，供通知与审计消费。
          
      en: >
          Status change event consumed by notification and audit.
          
deps:
  - kind: call
    to: oa.workflow.runtime.instance.guards
    from_api: "POST /api/v1/flow-instances/{instance_id}/transitions"
    to_api: "POST /api/v1/flow-instances/{instance_id}/transition-guard"
    label: {zh: "迁移前走守卫", en: "Run transition guards"}
  - kind: event
    to: oa.notify.inbox
    from_api: "kafka:oa.workflow.instance.status-changed"
    label: {zh: "通知发起人", en: "Notify initiator"}
  - kind: dataflow
    to: oa.audit.oplog
    from_api: "kafka:oa.workflow.instance.status-changed"
    label: {zh: "状态变更写日志", en: "Status change to log"}
---
