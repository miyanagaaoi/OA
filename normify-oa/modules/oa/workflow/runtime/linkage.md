---
uid: 48913a52
id: oa.workflow.runtime.linkage
parent: oa.workflow.runtime
state: planned
name: {zh: "联动关闭规则", en: "Linkage Closing Rules"}
description:
  zh: >
      状态联动：任一节点实例驳回→实例已驳回、同节点其余任务自动关闭、其余节点实例取消；会签达阈值或或签任一人通过→节点通过、未处理任务关闭并推进；实例进入终态→全部进行中节点与任务取消/关闭并通知发起人；不涉及费用→节点②标记已跳过并在轨迹留说明（否则会出现挂单）。
      
  en: >
      State linkage: a node rejection rejects the instance, auto-closes sibling tasks and cancels other node instances; reaching the countersign threshold or any single any-sign approval passes the node, closes remaining tasks and advances; entering a terminal state cancels or closes all open nodes and tasks and notifies the initiator; a no-cost matter marks node ② skipped with a note in the trace.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.768Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 492
    end_line: 507
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow-instances/{instance_id}/linkage-close"
    description:
      zh: >
          对驳回、阈值通过、跳过或终态执行联动关闭。
          
      en: >
          Applies linkage closing for a rejection, threshold pass, skip or terminal state.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow-instances/{instance_id}/linkage-rules"
    description:
      zh: >
          读取联动规则矩阵（触发到结果）。
          
      en: >
          Reads the linkage rule matrix (trigger to effect).
          
  - protocol: kafka
    path: "oa.workflow.instance.linkage-closed"
    description:
      zh: >
          联动结果事件，供轨迹与通知消费。
          
      en: >
          Linkage result event for tracing and notification.
          
deps:
  - kind: call
    to: oa.workflow.task
    from_api: "POST /api/v1/flow-instances/{instance_id}/linkage-close"
    label: {zh: "关闭同节点任务", en: "Close sibling tasks"}
  - kind: call
    to: oa.workflow.runtime.node-instance.state
    from_api: "POST /api/v1/flow-instances/{instance_id}/linkage-close"
    to_api: "PUT /api/v1/flow-node-instances/{node_instance_id}/status"
    label: {zh: "取消其余节点", en: "Cancel other nodes"}
  - kind: dataflow
    to: oa.audit.trace
    from_api: "kafka:oa.workflow.instance.linkage-closed"
    label: {zh: "跳过说明入轨迹", en: "Skip note into trace"}
  - kind: event
    to: oa.notify.inbox
    from_api: "kafka:oa.workflow.instance.linkage-closed"
    label: {zh: "结果通知发起人", en: "Result to initiator"}
  - kind: call
    to: oa.workflow.runtime.instance.state
    from_api: "POST /api/v1/flow-instances/{instance_id}/linkage-close"
    to_api: "POST /api/v1/flow-instances/{instance_id}/transitions"
    label: {zh: "驱动实例终态", en: "Drive instance status"}
---
