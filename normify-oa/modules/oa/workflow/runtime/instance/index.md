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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.811Z"
fingerprint: 1ebf4aee4ac8648bfaec0ea0afbfecc1b3e2f5fecc0dff532be914d9e85be0f2
source:
  - path: "doc/prd-0.1.md"
    line: 473
    end_line: 486
  - path: "doc/data-model.md"
    line: 379
    end_line: 418
deps:
  - kind: reference
    to: oa.workflow.approver.snapshot
    label: {zh: "以快照为权威", en: "Snapshot is authority"}
---
