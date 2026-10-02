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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.675Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
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
