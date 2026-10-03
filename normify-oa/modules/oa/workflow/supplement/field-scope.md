---
uid: 4c23cc13
id: oa.workflow.supplement.field-scope
parent: oa.workflow.supplement
state: planned
name: {zh: "补件字段边界", en: "Supplement Field Scope"}
description:
  zh: >
      补件只开放附件与补件说明字段；金额、对方主体、事项类别等已审批主字段一律只读——发起人若确需修改主字段，只能走「驳回 → 修改 → 重新提交」从①重走；该边界必须由服务端强制，避免补件时偷改内容。
      
  en: >
      During a supplement only attachments and the supplement note are writable; amount, counterparty, category and every other already-approved main field stay read-only. If the initiator truly must change a main field the only path is reject, modify and resubmit from node one. The boundary is enforced server-side so supplements cannot smuggle content changes.
      
revision: e3b34a3c59417096ade647fab4261b06f6b605e2
updated_at: "2026-10-03T02:30:28.830Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 328
    end_line: 330
apis:
  - protocol: http
    method: GET
    path: "/api/v1/flow/supplements/{supplement_id}/writable-fields"
    description:
      zh: >
          补件可写字段清单（仅附件与补件说明）。
          
      en: >
          Fields writable during supplement: attachments and the note only.
          
  - protocol: http
    method: POST
    path: "/api/v1/flow/supplements/{supplement_id}/main-field-guard"
    description:
      zh: >
          主字段只读校验（拒绝任何主字段写入）。
          
      en: >
          Guard rejecting any write to already-approved main fields.
          
deps:
  - kind: reference
    to: oa.form.matter
    from_api: "GET /api/v1/flow/supplements/{supplement_id}/writable-fields"
    label: {zh: "事项主字段定义", en: "Matter main-field definitions"}
---
