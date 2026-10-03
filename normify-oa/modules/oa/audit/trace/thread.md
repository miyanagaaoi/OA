---
uid: 08db55b0
id: oa.audit.trace.thread
parent: oa.audit.trace
state: planned
name: {zh: "轨迹事件写入", en: "Trail Event Writer"}
description:
  zh: >
      审批轨迹只追加写入：记录轨迹顺序、动作（提交/通过/驳回/流转/回退/补充材料请求/补充材料提交/转办/改派/加签/撤回/终止/跳过/归档登记/抄送）、节点实例、意见以及操作人姓名与职务快照，防止改名后轨迹失真。
      
  en: >
      Append-only approval trail writer: sequence, action (submit/approve/reject/route/rollback/supplement_request/supplement_submit/transfer/reassign/add_sign/withdraw/terminate/skip/archive_register/cc), node instance, opinion and actor name/position snapshots so later renames cannot distort the trail.
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.169Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
    line: 446
    end_line: 446
  - path: "doc/data-model.md"
    line: 703
    end_line: 718
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
