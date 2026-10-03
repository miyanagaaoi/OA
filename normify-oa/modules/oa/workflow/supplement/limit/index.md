---
uid: 4d11d111
id: oa.workflow.supplement.limit
parent: oa.workflow.supplement
name: {zh: "补件次数与时限", en: "Supplement Quota & Deadline"}
description:
  zh: >
      补件次数与时限控制：同一节点最多请求 1 次（节点实例 supplement_requested）、全单最多 3 次（实例 supplement_count），超出后审批人只能选择通过、驳回或终止；时限默认 3 个工作日，超时仅催办发起人，不自动驳回、不自动通过。
      
  en: >
      Supplement quantity and deadline control: at most one request per node and three per document, after which the approver may only pass, reject or terminate; the deadline defaults to three working days and an overdue supplement only reminds the initiator, never auto-rejecting or auto-passing.
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.421Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-023`（§6.3.1 集团层流转机制）
