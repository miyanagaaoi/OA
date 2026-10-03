---
uid: 6e7f21b0
id: oa.workflow.exception.withdraw
parent: oa.workflow.exception
state: planned
name: {zh: "撤回", en: "Withdraw"}
description:
  zh: >
      仅发起人可撤回，且仅限财务部复核节点（节点②）审批通过前；撤回后实例状态置「已撤回」并回到草稿，发起人可修改后重新提交；撤回动作通知相关审批人并写入轨迹。
      
  en: >
      Only the initiator may withdraw, and only before the finance node has approved. Withdrawing sets the instance to withdrawn and back to draft, after which the initiator may modify and resubmit; the action notifies the involved approvers and is written to the trail.
      
revision: c22d447e6e63ccb0edfd9624026f21e8d1413077
updated_at: "2026-10-03T02:20:07.746Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 388
    end_line: 388
  - path: "doc/prd-0.1.md"
    line: 353
    end_line: 353
  - path: "doc/prd-0.1.md"
    line: 481
    end_line: 481
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
