---
uid: 1c6797cf
id: oa.workflow.task.transfer
parent: oa.workflow.task
state: planned
name: {zh: "转办与改派", en: "Transfer & Reassignment"}
description:
  zh: >
      任务处理人的两种变更方式：转办由审批人本人发起，须填写原因，且转办对象必须是同一数据域内可见该单据的人（含跨公司），转办后原审批人失去该任务；改派仅系统管理员可用，用于人员离职或快照审批人不可用，同样必须填写原因并全程留痕。
      
  en: >
      Two ways to change a task owner: transfer, raised by the approver with a mandatory reason and only to people inside the same data scope who can see the document; and reassignment, restricted to system administrators for departed or unavailable snapshot approvers, also with a mandatory audited reason.
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.460Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-018`（§6.6 异常路径）
