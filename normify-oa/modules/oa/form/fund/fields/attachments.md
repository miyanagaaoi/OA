---
uid: 446865c8
id: oa.form.fund.fields.attachments
parent: oa.form.fund.fields
state: planned
name: {zh: "资金单附件字段组", en: "Fund Attachments Group"}
description:
  zh: >
      资金单附件 attachments（files、必填、≥1 个，发票/合同/说明，格式与大小见 1.4）；发起后仅补件可写。
      
  en: >
      Fund attachments `attachments` (files, required, ≥1, invoices/contracts/statements, format and size per 1.4); after initiation they are writable only during supplement.
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.208Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
    line: 113
    end_line: 113
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/fund/field-groups/attachments"
    description:
      zh: >
          资金单附件字段组定义。
          
      en: >
          Fund attachment field group definition.
          
  - protocol: http
    method: PUT
    path: "/api/v1/forms/fund/instances/{instance_id}/attachments"
    description:
      zh: >
          落存资金单附件（≥1，仅补件可写）。
          
      en: >
          Stores fund attachments (≥1, writable only during supplement).
          
deps:
  - kind: call
    to: oa.form.template.attachment
    from_api: "PUT /api/v1/forms/fund/instances/{instance_id}/attachments"
    label: {zh: "复用附件通用限制", en: "Reuses attachment limits"}
---
