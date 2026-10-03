---
uid: 36508cc4
id: oa.workflow.runtime.instance
parent: oa.workflow.runtime
name: {zh: "实例状态机", en: "Instance State Machine"}
description:
  zh: >
      flow_instance 的主状态机与迁移守卫：draft→approving→approved/rejected/withdrawn/terminated，sub_status 承载「待补件」，并在迁移前校验终态不可再提交、撤回窗口与计数闸门（REQ-FLOW-001/009/010/014/017/024）。
      
  en: >
      flow_instance main state machine and transition guards: draft→approving→approved/rejected/withdrawn/terminated with sub_status carrying "awaiting supplement", validating that terminal states cannot be resubmitted and enforcing the withdraw window and counter gates (REQ-FLOW-001/009/010/014/017/024).
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.159Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
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
