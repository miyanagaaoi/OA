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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.661Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
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
