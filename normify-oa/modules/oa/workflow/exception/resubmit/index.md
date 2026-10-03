---
uid: 70ac17bf
id: oa.workflow.exception.resubmit
parent: oa.workflow.exception
state: planned
name: {zh: "重新提交与快照重解析", en: "Resubmission & Snapshot Re-resolution"}
description:
  zh: >
      已驳回或已撤回的单据回到草稿后重新提交：重新解析审批人快照与流程版本（按最新模板），已审过的节点不保留，从①重走；重新提交是快照策略与异常路径的衔接点（REQ-FLOW-017）。
      
  en: >
      A rejected or withdrawn document returns to draft; on resubmission the approver snapshot and process version are resolved again against the latest template and reviewed nodes are discarded, so the flow restarts from node one. This is where the immutable snapshot strategy meets the exception paths.
      
revision: 112ab0a1d46779714029044fc8e0b46627804f30
updated_at: "2026-10-03T01:51:21.701Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 389
    end_line: 389
  - path: "doc/prd-0.1.md"
    line: 380
    end_line: 380
---
