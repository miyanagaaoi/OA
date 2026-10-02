---
uid: 05f05664
id: oa.workflow.task.decision
parent: oa.workflow.task
state: planned
name: {zh: "节点决议模式与阈值", en: "Node Decision Modes & Thresholds"}
description:
  zh: >
      每个节点可独立配置的决议方式：或签（任一人通过即通过，其余任务自动关闭，系统默认）、会签（同意人数达通过阈值后通过，任一人驳回则节点驳回）、依次审批（候选人按序串行，全部通过才通过）；阈值支持百分比与绝对人数两种写法，并含意见必填、候选人去重等决议前置校验。
  en: >
      Per-node decision configuration: any-sign (one approval passes the node, default), countersign (pass once approvals reach the configured threshold, any rejection rejects the node) and sequential (approvers act in order and all must approve); thresholds accept a percentage or an absolute headcount, together with opinion and deduplication pre-checks.
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:10:00Z"
fingerprint: pending
source:
  - path: "doc/prd-0.1.md"
    line: 218
    end_line: 232
  - path: "doc/prd-0.1.md"
    line: 548
    end_line: 549
---
