---
uid: 29fe9dff
id: oa.workflow.routing.forward
parent: oa.workflow.routing
state: planned
name: {zh: "集团层流转", en: "Group-level Routing"}
description:
  zh: >
      集团层链式流转：②及之后节点的审批人可指定下一个承接部门接手，单据继续在集团层流转并支持连续流转 A→B→C；必须选择承接部门并填写流转原因，承接部门须对本案可见；流转写入 flow_routing 并推进流转序号与当前承接部门。
      
  en: >
      Group-level chained routing: approvers at the finance node and later may designate the next receiving department and keep the document inside the group layer, supporting continuous chains A-B-C; a receiving department and a reason are mandatory and the department must be able to see the document. Routing writes flow_routing and advances the routing sequence and current department.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.605Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-020`（§6.3.1 集团层流转机制）
- `doc/data-model.md` → `CREATE TABLE flow_routing`（§5. 流程运行时）
