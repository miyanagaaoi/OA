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
      
revision: 44fc7aba1c7e884ffa3553faf31bfce974b388a9
updated_at: "2026-10-03T04:12:20.822Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-002`（§6.4 流程引擎核心能力）
