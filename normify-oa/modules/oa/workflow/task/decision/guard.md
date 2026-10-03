---
uid: 1241a0a4
id: oa.workflow.task.decision.guard
parent: oa.workflow.task.decision
state: planned
name: {zh: "决议前置校验", en: "Decision Pre-checks"}
description:
  zh: >
      决议前置校验：驳回必须填写意见且不少于 5 字，不允许空白驳回；同一人在同一节点出现多次时自动去重，只保留一条候选人与一条任务；同一人同时是多个串行节点审批人时，默认逐节点分别审批，不做连续节点自动合并。
      
  en: >
      Pre-checks before a decision is recorded: rejections require an opinion of at least five characters (no blank rejection); a person appearing several times in one node is deduplicated into a single candidate and task; the same person approving several sequential nodes is handled node by node, not merged.
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.321Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 237
    end_line: 237
  - path: "doc/prd-0.1.md"
    line: 384
    end_line: 384
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/guards/rejection-opinion"
    description:
      zh: >
          校验驳回意见（非空白且不少于 5 字）。
          
      en: >
          Validate a rejection opinion (non-blank, at least five characters).
          
  - protocol: http
    method: POST
    path: "/api/v1/flow/guards/approver-dedupe"
    description:
      zh: >
          候选人同一节点去重（同一人只保留一条）。
          
      en: >
          Deduplicate candidates within one node.
          
  - protocol: http
    method: POST
    path: "/api/v1/flow/guards/sequential-split"
    description:
      zh: >
          串行节点同一审批人拆分为逐节点任务（不自动合并）。
          
      en: >
          Split one approver across sequential nodes instead of merging them.
          
deps:
  - kind: call
    to: oa.workflow.task.record
    from_api: "POST /api/v1/flow/guards/approver-dedupe"
    to_api: "mysql:flow_task"
    label: {zh: "去重后写回任务", en: "Write the deduplicated tasks"}
---
