---
uid: 1e33ad03
id: oa.workflow.task.transfer.handover
parent: oa.workflow.task.transfer
state: planned
name: {zh: "转办", en: "Transfer"}
description:
  zh: >
      审批人将本人任务转办给他人：必须填写转办原因；转办对象必须是同一数据域内可见该单据的人（含跨公司），由可见性规则过滤候选人；转办后原审批人失去该任务，flow_task 记录原处理人与转办原因，转办记录写入审批轨迹。
      
  en: >
      An approver transfers the task: a reason is mandatory and the target must be someone inside the same data scope who can see the document. The original approver loses the task; the previous assignee and the reason are stored on flow_task and the transfer is written to the approval trail.
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.792Z"
fingerprint: 4421092e639ef98e9cd4aac53b5975054268b604256d140e3544a8a3c2ba5551
source:
  - path: "doc/prd-0.1.md"
    line: 390
    end_line: 390
  - path: "doc/prd-0.1.md"
    line: 202
    end_line: 202
  - path: "doc/data-model.md"
    line: 457
    end_line: 462
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/tasks/{task_id}/transfer"
    description:
      zh: >
          转办本人任务（原因必填、对象须同域可见）。
          
      en: >
          Transfer the task with a mandatory reason to a visible person.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow/tasks/{task_id}/transfer-candidates"
    description:
      zh: >
          可转办对象清单（同一数据域内可见该单据的人）。
          
      en: >
          Transfer candidates visible for this document.
          
  - protocol: kafka
    path: "oa.workflow.task.transferred"
    description:
      zh: >
          任务转办事件（通知新处理人并记轨迹）。
          
      en: >
          Event emitted when a task is transferred.
          
deps:
  - kind: call
    to: oa.authz.visibility
    from_api: "GET /api/v1/flow/tasks/{task_id}/transfer-candidates"
    label: {zh: "可见该单据的人判定", en: "Check document visibility"}
  - kind: call
    to: oa.notify.inbox
    from_api: "kafka:oa.workflow.task.transferred"
    label: {zh: "通知新处理人待办", en: "Notify the new assignee"}
  - kind: reference
    to: oa.workflow.task.record
    to_api: "mysql:flow_task"
    label: {zh: "更新处理人与转办原因", en: "Update assignee and reason"}
---
