---
uid: 236ee21f
id: oa.workflow.task.addsign.post
parent: oa.workflow.task.addsign
state: planned
name: {zh: "后加签", en: "Add-sign After"}
description:
  zh: >
      后加签：原审批人通过后不立即推进节点，而是把任务移交加签人再审，加签人签署意见后才计入节点结果；加签人对结果负责，加签链与加签动作记入审计日志与审批轨迹。
      
  en: >
      Add-sign after: once the original approver passes, the node is not advanced immediately - the task is handed to the added signer, whose opinion then counts towards the node result. The chain and the action are written to the audit log and trail.
      
revision: e3b34a3c59417096ade647fab4261b06f6b605e2
updated_at: "2026-10-03T02:30:28.836Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 346
    end_line: 346
  - path: "doc/prd-0.1.md"
    line: 392
    end_line: 392
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/tasks/{task_id}/add-sign/after"
    description:
      zh: >
          登记后加签人（本人审完后加签人再审）。
          
      en: >
          Register an added signer to act after the current approver.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow/tasks/{task_id}/add-sign/after-state"
    description:
      zh: >
          后加签任务的挂起与移交状态。
          
      en: >
          Read the held/handed-over state of an add-sign-after task.
          
deps:
  - kind: call
    to: oa.workflow.task.record
    from_api: "POST /api/v1/flow/tasks/{task_id}/add-sign/after"
    to_api: "mysql:flow_task"
    label: {zh: "移交原任务给加签人", en: "Hand task to added signer"}
---
