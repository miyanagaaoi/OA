---
uid: 715e58da
id: oa.form.contract.fields.attachments
parent: oa.form.contract.fields
state: planned
name: {zh: "合同附件字段组", en: "Contract Attachments Group"}
description:
  zh: >
      合同文本附件 attachments（files、必填、≥1 个）与对方资质附件 counterparty_docs（files、非必填）；均按附件通用限制，发起后仅补件可写。
      
  en: >
      Contract text attachments `attachments` (files, required, ≥1) and counterparty credential attachments `counterparty_docs` (files, optional); both follow the shared attachment limits and are writable only during supplement after initiation.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.655Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
source:
  - path: "doc/forms.md"
    line: 141
    end_line: 142
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/contract/field-groups/attachments"
    description:
      zh: >
          合同单附件字段组定义。
          
      en: >
          Contract attachment field group definition.
          
  - protocol: http
    method: PUT
    path: "/api/v1/forms/contract/instances/{instance_id}/attachments"
    description:
      zh: >
          落存合同文本与对方资质附件。
          
      en: >
          Stores contract text and counterparty credential attachments.
          
deps:
  - kind: call
    to: oa.form.template.attachment
    from_api: "PUT /api/v1/forms/contract/instances/{instance_id}/attachments"
    label: {zh: "复用附件通用限制", en: "Reuses attachment limits"}
---
