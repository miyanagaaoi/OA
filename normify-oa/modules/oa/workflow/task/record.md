---
uid: 01fbcdef
id: oa.workflow.task.record
parent: oa.workflow.task
name: {zh: "审批任务台账", en: "Approval Task Ledger"}
description:
  zh: >
      审批任务的落库与查询：会签节点同一节点实例生成多条任务，一条一人；维护任务状态机（待处理/已同意/已拒绝/已转办/已改派/已加签/已流转/已回退/已请求补件/已自动关闭）、审批意见与决议时间；提供待办列表与任务详情，并在他人已决议或单据进入终态时自动关闭任务。
      
  en: >
      The approval-task ledger: one row per approver on countersign nodes, the task state machine (pending/agreed/rejected/transferred/reassigned/added_sign/routed/returned/supplement/closed), opinion and decision time, the to-do list and task detail, plus automatic closing when another approver has decided or the document reached a terminal state.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.390Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/data-model.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: mysql
    path: "flow_task"
    description:
      zh: >
          审批任务表（会签 = 同节点实例多条），承载处理人、意见与任务状态。
          
      en: >
          Approval task table (one row per approver on countersign nodes): assignee, opinion and status.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow/tasks/todo"
    description:
      zh: >
          当前用户待办任务列表（按处理人与状态）。
          
      en: >
          To-do task list for the current user.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow/tasks/{task_id}"
    description:
      zh: >
          任务详情：所属节点、候选人、可执行动作。
          
      en: >
          Task detail with node, candidates and available actions.
          
  - protocol: http
    method: POST
    path: "/api/v1/flow/tasks/{task_id}/close"
    description:
      zh: >
          自动关闭任务（他人已决议或单据进入终态）。
          
      en: >
          Auto-close a task after another decision or a terminal state.
          
deps:
  - kind: call
    to: oa.workflow.runtime
    from_api: "POST /api/v1/flow/tasks/{task_id}/close"
    label: {zh: "任务关闭驱动状态联动", en: "Close task and cascade state"}
---

## 证据锚点
- `doc/data-model.md` → `CREATE TABLE flow_task`（§5. 流程运行时）
- `doc/prd-0.1.md` → `### 7.2 状态机`（§7.2 状态机）
