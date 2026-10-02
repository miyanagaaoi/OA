---
uid: 470ee95a
id: oa.workflow.supplement.submit
parent: oa.workflow.supplement
state: planned
name: {zh: "补件提交与回原节点", en: "Submit Supplement & Return"}
description:
  zh: >
      发起人提交补件：仅可新增附件（flow_attachment.round 等于本次补件轮次）与 ≤500 字的补件说明。提交后清空子状态 pending_supplement、supplement_count +1、补件记录置已补件，节点实例回到 active，任务回到请求补件的审批人。补件不算驳回，不写驳回记录。
      
  en: >
      The initiator submits the supplement: only newly uploaded attachments (flow_attachment.round equals the supplement round) and a supplement note of up to 500 characters. Submission clears the sub-status, increments supplement_count, marks the record submitted and returns the node to active with tasks back to the requesting approver. A supplement is not a rejection and never writes a rejection record.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.773Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
source:
  - path: "doc/prd-0.1.md"
    line: 325
    end_line: 325
  - path: "doc/prd-0.1.md"
    line: 331
    end_line: 331
  - path: "doc/prd-0.1.md"
    line: 502
    end_line: 502
  - path: "doc/data-model.md"
    line: 511
    end_line: 515
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/supplements/{supplement_id}/submit"
    description:
      zh: >
          提交补件（附件 + 补件说明），回到请求节点。
          
      en: >
          Submit the supplement (attachments + note) and return to the requesting node.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow/supplements/{supplement_id}"
    description:
      zh: >
          补件详情（要求、轮次、时限、已提交内容）。
          
      en: >
          Supplement detail: request, round, deadline and submission.
          
  - protocol: kafka
    path: "oa.workflow.supplement.submitted"
    description:
      zh: >
          补件提交事件（节点恢复进行中、任务回到请求人）。
          
      en: >
          Event emitted when the supplement is submitted.
          
deps:
  - kind: call
    to: oa.workflow.runtime
    from_api: "kafka:oa.workflow.supplement.submitted"
    label: {zh: "节点恢复进行中", en: "Resume the node"}
  - kind: call
    to: oa.workflow.task
    from_api: "kafka:oa.workflow.supplement.submitted"
    label: {zh: "任务交回请求人", en: "Return task to requester"}
---
