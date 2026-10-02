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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.773Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 389
    end_line: 389
  - path: "doc/prd-0.1.md"
    line: 380
    end_line: 380
---
