---
uid: 36508cc4
id: oa.workflow.runtime.instance
parent: oa.workflow.runtime
state: planned
name: {zh: "实例状态机", en: "Instance State Machine"}
description:
  zh: >
      flow_instance 的主状态机与迁移守卫：draft→approving→approved/rejected/withdrawn/terminated，sub_status 承载「待补件」，并在迁移前校验终态不可再提交、撤回窗口与计数闸门（REQ-FLOW-001/009/010/014/017/024）。
      
  en: >
      flow_instance main state machine and transition guards: draft→approving→approved/rejected/withdrawn/terminated with sub_status carrying "awaiting supplement", validating that terminal states cannot be resubmitted and enforcing the withdraw window and counter gates (REQ-FLOW-001/009/010/014/017/024).
      
revision: 966907fad0c5f0d01bc6a76ddba80bbbf67f586a
updated_at: "2026-10-03T04:41:37.446Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
deps:
  - kind: reference
    to: oa.workflow.approver.snapshot
    label: {zh: "以快照为权威", en: "Snapshot is authority"}
---

## 证据锚点
- `doc/prd-0.1.md` → `### 7.2 状态机`（§7.2 状态机）
- `doc/data-model.md` → `CREATE TABLE flow_instance`（§5. 流程运行时）
