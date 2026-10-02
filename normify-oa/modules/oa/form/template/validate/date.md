---
uid: 11960a55
id: oa.form.template.validate.date
parent: oa.form.template.validate
state: planned
name: {zh: "日期与区间校验", en: "Date Validation"}
description:
  zh: >
      日期不早于今天（部分字段为不早于发起日）、日期区间结束 ≥ 开始；提示「{标签}不能早于今天」「结束日期不能早于开始日期」。
      
  en: >
      Dates must not precede today (some fields must not precede the initiation date) and a date range's end must be ≥ its start; messages are 「{标签}不能早于今天」 and 「结束日期不能早于开始日期」.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.680Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
source:
  - path: "doc/forms.md"
    line: 44
    end_line: 45
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/{form_type}/validate/date"
    description:
      zh: >
          单日期下限校验。
          
      en: >
          Validates a single date's lower bound.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/{form_type}/validate/date-range"
    description:
      zh: >
          日期区间顺序校验。
          
      en: >
          Validates date-range ordering.
          
---
