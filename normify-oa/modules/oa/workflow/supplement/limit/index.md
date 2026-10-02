---
uid: 4d11d111
id: oa.workflow.supplement.limit
parent: oa.workflow.supplement
state: planned
name: {zh: "补件次数与时限", en: "Supplement Quota & Deadline"}
description:
  zh: >
      补件次数与时限控制：同一节点最多请求 1 次（节点实例 supplement_requested）、全单最多 3 次（实例 supplement_count），超出后审批人只能选择通过、驳回或终止；时限默认 3 个工作日，超时仅催办发起人，不自动驳回、不自动通过。
  en: >
      Supplement quantity and deadline control: at most one request per node and three per document, after which the approver may only pass, reject or terminate; the deadline defaults to three working days and an overdue supplement only reminds the initiator, never auto-rejecting or auto-passing.
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:10:00Z"
fingerprint: pending
source:
  - path: "doc/prd-0.1.md"
    line: 325
    end_line: 325
  - path: "doc/prd-0.1.md"
    line: 333
    end_line: 333
  - path: "doc/prd-0.1.md"
    line: 554
    end_line: 555
---
