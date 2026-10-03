---
uid: 01fbcdef
id: oa.workflow.task.record
parent: oa.workflow.task
state: planned
name: {zh: "审批任务台账", en: "Approval Task Ledger"}
description:
  zh: >
      审批任务的落库与查询：会签节点同一节点实例生成多条任务，一条一人；维护任务状态机（待处理/已同意/已拒绝/已转办/已改派/已加签/已流转/已回退/已请求补件/已自动关闭）、审批意见与决议时间；提供待办列表与任务详情，并在他人已决议或单据进入终态时自动关闭任务。
      
  en: >
      The approval-task ledger: one row per approver on countersign nodes, the task state machine (pending/agreed/rejected/transferred/reassigned/added_sign/routed/returned/supplement/closed), opinion and decision time, the to-do list and task detail, plus automatic closing when another approver has decided or the document reached a terminal state.
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.323Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/data-model.md"
    line: 525
    end_line: 554
  - path: "doc/prd-0.1.md"
    line: 510
    end_line: 510
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
