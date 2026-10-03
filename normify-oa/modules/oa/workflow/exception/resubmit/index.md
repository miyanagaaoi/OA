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
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.148Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-017`（§6.6 异常路径）
