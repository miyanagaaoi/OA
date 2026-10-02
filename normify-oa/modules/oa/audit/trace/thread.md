---
uid: 08db55b0
id: oa.audit.trace.thread
parent: oa.audit.trace
state: planned
name: {zh: "轨迹事件写入", en: "Trail Event Writer"}
description:
  zh: >
      审批轨迹只追加写入：记录轨迹顺序、动作（提交/通过/驳回/转办/加签/流转/回退/补件/撤回/终止/归档/抄送）、节点实例、意见以及操作人姓名与职务快照，防止改名后轨迹失真。
      
  en: >
      Append-only approval trail writer: sequence, action (submit/approve/reject/transfer/add-sign/route/return/supplement/withdraw/terminate/archive/cc), node instance, opinion and actor name/position snapshots so later renames cannot distort the trail.
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.663Z"
fingerprint: 6be246c6834b9b56d4a42d5b955150bb6a06a766a90c031fd7f3af6b4c917a6d
source:
  - path: "doc/prd-0.1.md"
    line: 427
    end_line: 427
  - path: "doc/data-model.md"
    line: 627
    end_line: 642
apis:
  - protocol: mysql
    path: "sys_thread"
    description:
      zh: >
          审批轨迹表（面向展示）。
          
      en: >
          Approval trail table (presentation oriented).
          
  - protocol: http
    method: POST
    path: "/api/v1/audit/trails/events"
    description:
      zh: >
          追加一条审批轨迹事件。
          
      en: >
          Appends one approval trail event.
          
deps:
  - kind: event
    to: oa.workflow.runtime
    from_api: "POST /api/v1/audit/trails/events"
    label: {zh: "接收流转与状态变更事件", en: "Consume routing events"}
  - kind: event
    to: oa.workflow.task
    from_api: "POST /api/v1/audit/trails/events"
    label: {zh: "接收审批与驳回事件", en: "Consume approval events"}
  - kind: event
    to: oa.workflow.supplement
    from_api: "POST /api/v1/audit/trails/events"
    label: {zh: "接收补件请求与补件提交事件", en: "Consume supplement events"}
  - kind: call
    to: oa.identity.user
    from_api: "POST /api/v1/audit/trails/events"
    label: {zh: "取姓名与职务快照", en: "Snapshot name and position"}
---
