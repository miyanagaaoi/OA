---
uid: 70ac17bf
id: oa.workflow.exception.resubmit
parent: oa.workflow.exception
name: {zh: "重新提交与快照重解析", en: "Resubmission & Snapshot Re-resolution"}
description:
  zh: >
      已驳回或已撤回的单据回到草稿后重新提交：重新解析审批人快照与流程版本（按最新模板），已审过的节点不保留，从①重走；重新提交是快照策略与异常路径的衔接点（REQ-FLOW-017）。
      
  en: >
      A rejected or withdrawn document returns to draft; on resubmission the approver snapshot and process version are resolved again against the latest template and reviewed nodes are discarded, so the flow restarts from node one. This is where the immutable snapshot strategy meets the exception paths.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.538Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-017`（§6.6 异常路径）
