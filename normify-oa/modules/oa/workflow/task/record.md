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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.777Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
source:
  - path: "doc/data-model.md"
    line: 449
    end_line: 473
  - path: "doc/prd-0.1.md"
    line: 460
    end_line: 460
  - path: "doc/prd-0.1.md"
    line: 490
    end_line: 490
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
