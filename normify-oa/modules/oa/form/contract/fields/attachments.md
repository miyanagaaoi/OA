---
uid: 715e58da
id: oa.form.contract.fields.attachments
parent: oa.form.contract.fields
name: {zh: "合同附件字段组", en: "Contract Attachments Group"}
description:
  zh: >
      合同文本附件 attachments（files、必填、≥1 个）与对方资质附件 counterparty_docs（files、非必填）；均按附件通用限制，发起后仅补件可写。
      
  en: >
      Contract text attachments `attachments` (files, required, ≥1) and counterparty credential attachments `counterparty_docs` (files, optional); both follow the shared attachment limits and are writable only during supplement after initiation.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.000Z"
fingerprint: 10c6effd215627f1c844a3acecef0995e0cae341763861c18553845cb71394d6
source:
  - path: "doc/forms.md"
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

## 证据锚点
- `doc/forms.md` → `## 4. 合同审批单（`form_type = contract`）`（§4. 合同审批单）
