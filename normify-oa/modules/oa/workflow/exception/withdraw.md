---
uid: 6e7f21b0
id: oa.workflow.exception.withdraw
parent: oa.workflow.exception
state: planned
name: {zh: "撤回", en: "Withdraw"}
description:
  zh: >
      仅发起人可撤回；撤回窗口是**模板级配置项**（flow_template.withdraw_window，doc/templates.md §1.8）：默认 until_finance_approved 允许撤回到财务部复核节点（节点②）通过之前（含②审批中，REQ-FLOW-009 口径，行为不变），可选 until_finance_started 仅在②开始处理前可撤（AC-16 严格口径）。引擎按**实例发起时锁定的模板版本**取该口径，故改模板不影响在途单据（AC-09）。撤回后实例回草稿，动作写入轨迹。
      
  en: >
      Only the initiator may withdraw. The window is a template-level setting (flow_template.withdraw_window, templates.md 1.8): the default until_finance_approved allows withdrawal until the finance node (node 2) approves, node 2 in approval included (REQ-FLOW-009); the optional until_finance_started allows it only before node 2 starts (strict AC-16). The engine reads it from the version locked at instance creation, so edits never affect in-flight documents (AC-09).
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.444Z"
fingerprint: 8d16cf2ade0b836c969a1d24ebfe8370cf368f4e374aabc5aa266a360273d630
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/templates.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/instances/{instance_id}/withdraw"
    description:
      zh: >
          发起人撤回（仅节点②通过前），撤回后回草稿。
          
      en: >
          Withdraw before the finance node passes; the document returns to draft.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow/instances/{instance_id}/withdrawable"
    description:
      zh: >
          判定当前是否仍可撤回。
          
      en: >
          Check whether withdrawal is still allowed.
          
deps:
  - kind: call
    to: oa.workflow.runtime
    from_api: "POST /api/v1/flow/instances/{instance_id}/withdraw"
    label: {zh: "置已撤回并回草稿", en: "Set withdrawn to draft"}
  - kind: call
    to: oa.notify.inbox
    from_api: "POST /api/v1/flow/instances/{instance_id}/withdraw"
    label: {zh: "通知相关审批人", en: "Notify involved approvers"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-009`（§6.4 流程引擎核心能力）
- `doc/templates.md` → `### 1.8 撤回窗口配置项`（§1.8）
