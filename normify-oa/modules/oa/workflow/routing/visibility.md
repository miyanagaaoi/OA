---
uid: 3bc091c4
id: oa.workflow.routing.visibility
parent: oa.workflow.routing
state: planned
name: {zh: "流转可见性", en: "Routing Visibility"}
description:
  zh: >
      承接部门对本案可见：flow_routing 流转链同时是「流转可见性」的判定依据，凡被流转到过的部门（以及当前承接部门）均可只读查看该单据；未承接部门与抄送人保持只读，不可审批。
      
  en: >
      A receiving department can see the document: the flow_routing chain is itself the basis for routing visibility, so every department the document has been routed to (and the current receiving department) may read it. Other departments and cc recipients stay read-only and may not approve.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.765Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
source:
  - path: "doc/prd-0.1.md"
    line: 322
    end_line: 322
  - path: "doc/prd-0.1.md"
    line: 338
    end_line: 338
  - path: "doc/data-model.md"
    line: 498
    end_line: 498
apis:
  - protocol: http
    method: GET
    path: "/api/v1/flow/instances/{instance_id}/visible-departments"
    description:
      zh: >
          由流转链推导的可视部门集合。
          
      en: >
          Set of departments that may read the document, derived from routing.
          
  - protocol: http
    method: POST
    path: "/api/v1/flow/instances/{instance_id}/visibility-check"
    description:
      zh: >
          判定某用户/部门是否可读该单据（区分只读与可审批）。
          
      en: >
          Decide whether a user or department may read, and whether they may approve.
          
deps:
  - kind: call
    to: oa.authz.visibility
    from_api: "POST /api/v1/flow/instances/{instance_id}/visibility-check"
    label: {zh: "数据域可见性判定", en: "Data-scope visibility check"}
---
