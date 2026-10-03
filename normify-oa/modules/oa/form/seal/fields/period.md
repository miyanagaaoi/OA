---
uid: 81de221f
id: oa.form.seal.fields.period
parent: oa.form.seal.fields
state: planned
name: {zh: "使用期限字段组", en: "Usage Period Field Group"}
description:
  zh: >
      使用开始日期 usage_start（date、必填、不早于今天）与使用结束日期 usage_end（date、必填、≥ usage_start）。
      
  en: >
      Usage start `usage_start` (date, required, not earlier than today) and usage end `usage_end` (date, required, ≥ start).
      
revision: e3b34a3c59417096ade647fab4261b06f6b605e2
updated_at: "2026-10-03T02:47:41.778Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
    line: 169
    end_line: 169
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/seal/field-groups/period"
    description:
      zh: >
          使用期限字段组定义。
          
      en: >
          Usage period field group definition.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/seal/fields/usage-period/validate"
    description:
      zh: >
          校验使用期限开始不早于今天且结束 ≥ 开始。
          
      en: >
          Validates that usage start is not earlier than today and end ≥ start.
          
---
