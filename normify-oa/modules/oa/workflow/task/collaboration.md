---
uid: 1919d059
id: oa.workflow.task.collaboration
parent: oa.workflow.task
state: planned
name: {zh: "并行协同任务组", en: "Parallel Collaboration Task Groups"}
description:
  zh: >
      集团归口节点（②）的审批人在审批时勾选协同部门，系统为每个被勾选部门取该部门负责人并生成独立任务组（每组独立决议）；全部协同任务组完成后流程才继续到下一节点，任一协同部门驳回则单据驳回。协同任务不占主干节点编号。
      
  en: >
      At the finance node the approver ticks collaborating departments; each department leader gets an independent task group with its own decision. The flow continues only after all groups finish, and any rejection returns the document to the initiator. Collaboration tasks consume no main-chain node number.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.775Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 301
    end_line: 301
  - path: "doc/prd-0.1.md"
    line: 348
    end_line: 348
  - path: "doc/prd-0.1.md"
    line: 212
    end_line: 212
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/node-instances/{node_instance_id}/collaboration-groups"
    description:
      zh: >
          勾选协同部门并生成独立任务组。
          
      en: >
          Tick collaborating departments and create their task groups.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow/instances/{instance_id}/collaboration-groups"
    description:
      zh: >
          查询协同任务组及其完成进度。
          
      en: >
          List collaboration groups and their progress.
          
  - protocol: http
    method: POST
    path: "/api/v1/flow/instances/{instance_id}/collaboration-groups/complete-check"
    description:
      zh: >
          全部协同组完成判定，满足则推进下一节点。
          
      en: >
          Check that every collaboration group finished before advancing.
          
  - protocol: kafka
    path: "oa.workflow.collaboration.completed"
    description:
      zh: >
          全部协同任务组完成事件。
          
      en: >
          Event emitted when all collaboration groups complete.
          
deps:
  - kind: call
    to: oa.workflow.approver
    from_api: "POST /api/v1/flow/node-instances/{node_instance_id}/collaboration-groups"
    label: {zh: "解析勾选部门负责人", en: "Resolve ticked dept leaders"}
  - kind: call
    to: oa.workflow.exception
    from_api: "kafka:oa.workflow.collaboration.completed"
    label: {zh: "协同驳回转异常路径", en: "Rejection -> exception path"}
---
