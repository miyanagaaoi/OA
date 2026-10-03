---
uid: 21e083c2
id: oa.workflow.task.addsign.pre
parent: oa.workflow.task.addsign
state: planned
name: {zh: "前加签", en: "Add-sign Before"}
description:
  zh: >
      前加签：在当前审批人之前插入加签人任务，加签人先审并签署意见，审完后任务回到原审批人继续处理；加签不改变节点的决议模式与通过阈值口径，加签人对自己的意见负责。
      
  en: >
      Add-sign before inserts the added signer ahead of the current approver; the signer decides first and, once finished, the task returns to the original approver. The node decision mode and threshold are unchanged and the signer owns their opinion.
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.815Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 346
    end_line: 346
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/tasks/{task_id}/add-sign/before"
    description:
      zh: >
          前加签：插入加签人任务并挂起原任务。
          
      en: >
          Insert the added signer task before the current one.
          
  - protocol: http
    method: POST
    path: "/api/v1/flow/tasks/{task_id}/add-sign/before/resume"
    description:
      zh: >
          加签人审完后把任务交回原审批人。
          
      en: >
          Return the task to the original approver after the signer decides.
          
deps:
  - kind: call
    to: oa.workflow.task.record
    from_api: "POST /api/v1/flow/tasks/{task_id}/add-sign/before"
    to_api: "mysql:flow_task"
    label: {zh: "生成加签人任务", en: "Create the added-signer task"}
  - kind: call
    to: oa.notify.inbox
    from_api: "POST /api/v1/flow/tasks/{task_id}/add-sign/before"
    label: {zh: "通知加签人", en: "Notify the added signer"}
---
