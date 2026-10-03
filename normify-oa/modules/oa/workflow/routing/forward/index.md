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
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.804Z"
fingerprint: 45de2030cdb84b6b065d6ae29d070a0561de3211039af1703055f01a49a9f2e4
source:
  - path: "doc/prd-0.1.md"
    line: 312
    end_line: 326
  - path: "doc/data-model.md"
    line: 475
    end_line: 498
---
