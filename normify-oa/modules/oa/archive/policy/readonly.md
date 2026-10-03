---
uid: 2bcc7114
id: oa.archive.policy.readonly
parent: oa.archive.policy
state: planned
name: {zh: "归档后只读", en: "Post-Archive Read-only"}
description:
  zh: >
      归档实例置为只读：不可审批、不可撤回、不可补件，仅保留按单号检索、详情预览与审计导出能力。
      
  en: >
      Marks archived instances read-only: no approval, no withdrawal and no supplementing, while document-number lookup, detail preview and audit export remain available.
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.654Z"
fingerprint: 45de2030cdb84b6b065d6ae29d070a0561de3211039af1703055f01a49a9f2e4
source:
  - path: "doc/data-model.md"
    line: 824
    end_line: 824
  - path: "doc/prd-0.1.md"
    line: 540
    end_line: 540
apis:
  - protocol: http
    method: POST
    path: "/api/v1/archive/instances/{biz_no}/freeze"
    description:
      zh: >
          把归档单据置为只读。
          
      en: >
          Freezes an archived document as read-only.
          
  - protocol: http
    method: GET
    path: "/api/v1/archive/instances/{biz_no}/capabilities"
    description:
      zh: >
          返回归档单据当前允许的操作。
          
      en: >
          Returns the allowed operations of an archived document.
          
deps:
  - kind: call
    to: oa.workflow.runtime
    from_api: "POST /api/v1/archive/instances/{biz_no}/freeze"
    label: {zh: "关闭审批与撤回入口", en: "Close approve/withdraw paths"}
  - kind: call
    to: oa.workflow.supplement
    from_api: "POST /api/v1/archive/instances/{biz_no}/freeze"
    label: {zh: "关闭补件入口", en: "Close supplement path"}
  - kind: call
    to: oa.portal.detail
    from_api: "GET /api/v1/archive/instances/{biz_no}/capabilities"
    label: {zh: "详情页切只读态", en: "Detail page read-only mode"}
---
