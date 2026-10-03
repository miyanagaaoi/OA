---
uid: 2d3d5e4b
id: oa.workflow.exception
parent: oa.workflow
state: planned
name: {zh: "异常路径", en: "Exception Paths"}
description:
  zh: >
      异常路径：驳回固定回到发起人（不允许空白驳回）、会签/协同驳回后其余任务自动关闭、仅发起人可在节点②通过前撤回、重提时重新解析快照与模板版本、管理员与集团分管领导可终止、超时仅催办不自动跳过不自动升级。
      
  en: >
      Exception paths: rejection always returns to the initiator (blank rejection forbidden), countersign or collaboration rejection closes the remaining tasks, withdrawal is allowed only before the Finance node approves, resubmission re-resolves the snapshot and template version, termination by admins or the group line leader, and timeouts only remind - never auto-skip.
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.442Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `### 6.6 异常路径（一期必须实现）`（§6.6 异常路径）
