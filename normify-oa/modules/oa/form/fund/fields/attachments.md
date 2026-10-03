---
uid: 446865c8
id: oa.form.fund.fields.attachments
parent: oa.form.fund.fields
name: {zh: "资金单附件字段组", en: "Fund Attachments Group"}
description:
  zh: >
      资金单附件 attachments（files、必填、≥1 个，发票/合同/说明，格式与大小见 1.4）；发起后仅补件可写。
      
  en: >
      Fund attachments `attachments` (files, required, ≥1, invoices/contracts/statements, format and size per 1.4); after initiation they are writable only during supplement.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.264Z"
fingerprint: 1266f6407e434b7ee473c37529842b35c2605a39bc4ad8881f6bc19d40d4caf1
source:
  - path: "doc/forms.md"
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

## 证据锚点
- `doc/forms.md` → `## 3. 资金审批单（`form_type = fund`）`（§3. 资金审批单）
