---
uid: 4545b0da
id: oa.workflow.runtime.supplement-state
parent: oa.workflow.runtime
name: {zh: "待补件子状态与回跳", en: "Awaiting-Supplement Sub-State"}
description:
  zh: >
      「待补件」是实例子状态而非驳回：进入时 sub_status=supplement、当前节点等待补件、该节点任务不可再审批、通知发起人；补件只开放附件与备注（主字段只读），提交后回到请求补件的节点继续，补件不算驳回、不计入驳回率（REQ-FLOW-023）。
      
  en: >
      "Awaiting supplement" is an instance sub-status rather than a rejection: on entry sub_status=supplement, the current node waits, its tasks can no longer be approved and the initiator is notified; only attachments and notes stay writable (main fields read-only), submission returns to the requesting node and the round never counts as a rejection (REQ-FLOW-023).
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.383Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/flow-instances/{instance_id}/supplement-state"
    description:
      zh: >
          读取实例的待补件子状态。
          
      en: >
          Reads the supplement sub-state of an instance.
          
  - protocol: http
    method: POST
    path: "/api/v1/flow-instances/{instance_id}/supplement-state"
    description:
      zh: >
          进入待补件子状态并暂停当前节点。
          
      en: >
          Enters the awaiting-supplement sub-state and pauses the node.
          
  - protocol: http
    method: POST
    path: "/api/v1/flow-instances/{instance_id}/supplement-state/resume"
    description:
      zh: >
          清除子状态并回跳到请求补件的节点。
          
      en: >
          Clears the sub-state and returns to the requesting node.
          
  - protocol: kafka
    path: "oa.workflow.instance.supplement-state-changed"
    description:
      zh: >
          子状态变更事件，供催办与审计消费。
          
      en: >
          Sub-state change event for reminders and audit.
          
deps:
  - kind: call
    to: oa.workflow.supplement
    from_api: "POST /api/v1/flow-instances/{instance_id}/supplement-state"
    label: {zh: "生成补件记录", en: "Create supplement record"}
  - kind: call
    to: oa.workflow.runtime.node-instance.state
    from_api: "POST /api/v1/flow-instances/{instance_id}/supplement-state"
    to_api: "PUT /api/v1/flow-node-instances/{node_instance_id}/status"
    label: {zh: "暂停/恢复节点", en: "Pause/resume node"}
  - kind: call
    to: oa.workflow.runtime.counters
    from_api: "POST /api/v1/flow-instances/{instance_id}/supplement-state/resume"
    to_api: "POST /api/v1/flow-instances/{instance_id}/counters/increment"
    label: {zh: "补件次数自增", en: "Count supplement round"}
  - kind: event
    to: oa.notify.mail
    from_api: "kafka:oa.workflow.instance.supplement-state-changed"
    label: {zh: "邮件通知发起人", en: "Mail the initiator"}
  - kind: event
    to: oa.notify.inbox
    from_api: "kafka:oa.workflow.instance.supplement-state-changed"
    label: {zh: "站内信通知发起人", en: "Inbox the initiator"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-023`（§6.3.1 集团层流转机制）
- `doc/data-model.md` → `CREATE TABLE flow_supplement`（§5. 流程运行时）
