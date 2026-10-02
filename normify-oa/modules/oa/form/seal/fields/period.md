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
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.714Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
    line: 162
    end_line: 163
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
