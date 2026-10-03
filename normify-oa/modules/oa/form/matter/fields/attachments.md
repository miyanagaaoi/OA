---
uid: "33050201"
id: oa.form.matter.fields.attachments
parent: oa.form.matter.fields
state: planned
name: {zh: "事项单附件字段组", en: "Matter Attachments Group"}
description:
  zh: >
      事项单附件 attachments（files、非必填、格式与大小见 1.4）；发起后为「仅补件」可写，补件时 round ≥ 1。
      
  en: >
      Matter attachments `attachments` (files, optional, format and size per 1.4); after initiation they are writable only during supplement, where round ≥ 1.
      
revision: c22d447e6e63ccb0edfd9624026f21e8d1413077
updated_at: "2026-10-03T02:20:07.635Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
    line: 87
    end_line: 87
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
