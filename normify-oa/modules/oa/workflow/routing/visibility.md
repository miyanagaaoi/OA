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
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.433Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-AUTH-004`（§5.3 数据域口径）
- `doc/data-model.md` → `CREATE TABLE flow_routing`（§5. 流程运行时）
