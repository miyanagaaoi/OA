---
uid: 21e083c2
id: oa.workflow.task.addsign.pre
parent: oa.workflow.task.addsign
name: {zh: "前加签", en: "Add-sign Before"}
description:
  zh: >
      前加签：在当前审批人之前插入加签人任务，加签人先审并签署意见，审完后任务回到原审批人继续处理；加签不改变节点的决议模式与通过阈值口径，加签人对自己的意见负责。
      
  en: >
      Add-sign before inserts the added signer ahead of the current approver; the signer decides first and, once finished, the task returns to the original approver. The node decision mode and threshold are unchanged and the signer owns their opinion.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.387Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-003`（§6.4 流程引擎核心能力）
