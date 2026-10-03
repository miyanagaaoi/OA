---
uid: 7f08b391
id: oa.form.seal.fields.basic
parent: oa.form.seal.fields
state: planned
name: {zh: "印鉴基础字段组", en: "Seal Basic Field Group"}
description:
  zh: >
      用印/借用事由 title（text≤60、必填）、事项类别 category（固定「行政」、默认行政、置灰不可选）、用途说明 purpose（textarea≤500、≥5 字符，is_external=是 时下限提升至 20 字符）、附件 attachments（files、非必填）。
      
  en: >
      Seal/borrow reason `title` (text ≤60, required), category `category` (fixed to admin, defaulted and greyed out), purpose `purpose` (textarea ≤500, ≥5 characters, raised to 20 when `is_external` is yes) and attachments `attachments` (files, optional).
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.220Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
    line: 157
    end_line: 161
  - path: "doc/forms.md"
    line: 165
    end_line: 165
  - path: "doc/forms.md"
    line: 168
    end_line: 168
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/seal/field-groups/basic"
    description:
      zh: >
          印鉴单基础字段组定义。
          
      en: >
          Basic field group definition for seal forms.
          
  - protocol: http
    method: PUT
    path: "/api/v1/forms/seal/instances/{instance_id}/draft/basic"
    description:
      zh: >
          保存印鉴单基础字段草稿。
          
      en: >
          Saves the seal basic field group draft.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/seal/fields/purpose/min-length-evaluate"
    description:
      zh: >
          求值用途说明的长度下限（对外时 20）。
          
      en: >
          Evaluates the purpose minimum length (20 when external).
          
---
