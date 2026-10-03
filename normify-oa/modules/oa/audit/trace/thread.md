---
uid: 08db55b0
id: oa.audit.trace.thread
parent: oa.audit.trace
name: {zh: "轨迹事件写入", en: "Trail Event Writer"}
description:
  zh: >
      审批轨迹只追加写入：记录轨迹顺序、动作（提交/通过/驳回/流转/回退/补充材料请求/补充材料提交/转办/改派/加签/撤回/终止/跳过/归档登记/抄送）、节点实例、意见以及操作人姓名与职务快照，防止改名后轨迹失真。
      
  en: >
      Append-only approval trail writer: sequence, action (submit/approve/reject/route/rollback/supplement_request/supplement_submit/transfer/reassign/add_sign/withdraw/terminate/skip/archive_register/cc), node instance, opinion and actor name/position snapshots so later renames cannot distort the trail.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.232Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-LOG-002`（§6.9 审计日志）
- `doc/data-model.md` → `CREATE TABLE sys_thread`（§6. 签名、附件、抄送、消息、审计）
