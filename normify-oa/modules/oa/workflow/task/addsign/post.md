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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.789Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
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
