---
uid: "33050201"
id: oa.form.matter.fields.attachments
parent: oa.form.matter.fields
name: {zh: "事项单附件字段组", en: "Matter Attachments Group"}
description:
  zh: >
      事项单附件 attachments（files、非必填、格式与大小见 1.4）；发起后为「仅补件」可写，补件时 round ≥ 1。
      
  en: >
      Matter attachments `attachments` (files, optional, format and size per 1.4); after initiation they are writable only during supplement, where round ≥ 1.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.269Z"
fingerprint: 1266f6407e434b7ee473c37529842b35c2605a39bc4ad8881f6bc19d40d4caf1
source:
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/matter/field-groups/attachments"
    description:
      zh: >
          事项单附件字段组定义。
          
      en: >
          Matter attachment field group definition.
          
  - protocol: http
    method: PUT
    path: "/api/v1/forms/matter/instances/{instance_id}/attachments"
    description:
      zh: >
          落存事项单附件（仅补件可写）。
          
      en: >
          Stores matter attachments (writable only during supplement).
          
deps:
  - kind: call
    to: oa.form.template.attachment
    from_api: "PUT /api/v1/forms/matter/instances/{instance_id}/attachments"
    label: {zh: "复用附件通用限制", en: "Reuses attachment limits"}
---

## 证据锚点
- `doc/forms.md` → `## 2. 事项审批单（`form_type = matter`）`（§2. 事项审批单）
