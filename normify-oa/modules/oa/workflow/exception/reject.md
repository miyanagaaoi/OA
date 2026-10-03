---
uid: 60b3dc5a
id: oa.workflow.exception.reject
parent: oa.workflow.exception
name: {zh: "驳回与固定回发起人", en: "Rejection & Fixed Return to Initiator"}
description:
  zh: >
      驳回必须填写意见且不少于 5 字，不允许空白驳回（REQ-FLOW-013）；驳回后去向固定为回到发起人，不给审批人选择权，实例状态置「已驳回」，发起人可修改后重新提交或终止（REQ-FLOW-014）；驳回写入审批轨迹与驳回记录，供驳回率统计。
      
  en: >
      A rejection requires an opinion of at least five characters, blank rejections are forbidden. After rejection the destination is always the initiator, with no choice offered to the approver: the instance becomes rejected and the initiator may modify and resubmit or terminate. Rejections are written to the trail and to the rejection record used for the rejection-rate report.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.538Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/tasks/{task_id}/reject"
    description:
      zh: >
          驳回任务（意见 ≥5 字，固定回发起人）。
          
      en: >
          Reject a task with an opinion of at least five characters.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow/instances/{instance_id}/rejection-record"
    description:
      zh: >
          驳回记录（不含补件，供驳回率统计）。
          
      en: >
          Rejection records excluding supplements, for rejection-rate stats.
          
  - protocol: kafka
    path: "oa.workflow.instance.rejected"
    description:
      zh: >
          实例驳回事件（通知发起人并关闭其余任务）。
          
      en: >
          Event emitted when the whole instance is rejected.
          
deps:
  - kind: call
    to: oa.workflow.runtime
    from_api: "POST /api/v1/flow/tasks/{task_id}/reject"
    label: {zh: "置已驳回并取消其余节点", en: "Set rejected, cancel rest"}
  - kind: call
    to: oa.audit.trace
    from_api: "kafka:oa.workflow.instance.rejected"
    label: {zh: "写审批轨迹与驳回记录", en: "Write the approval trail"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-014`（§6.3.1 集团层流转机制）
- `doc/data-model.md` → `CREATE TABLE flow_instance`（§5. 流程运行时）
