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
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.617Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-003`（§6.4 流程引擎核心能力）
