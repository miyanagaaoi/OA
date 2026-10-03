---
uid: 11960a55
id: oa.form.template.validate.date
parent: oa.form.template.validate
name: {zh: "日期与区间校验", en: "Date Validation"}
description:
  zh: >
      日期不早于今天（部分字段为不早于发起日）、日期区间结束 ≥ 开始；提示「{标签}不能早于今天」「结束日期不能早于开始日期」。
      
  en: >
      Dates must not precede today (some fields must not precede the initiation date) and a date range's end must be ≥ its start; messages are 「{标签}不能早于今天」 and 「结束日期不能早于开始日期」.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.464Z"
fingerprint: 8c925f5e1097577033569ef7cc3590e51284551800485f9474b6835096b34430
source:
  - path: "doc/forms.md"
  - path: "oa-server/src/main/java/com/oa/form/template/validate/FormPayloadValidator.java"
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

## 证据锚点
- `doc/forms.md` → `### 1.3 通用校验规则`（§1.3 通用校验规则）
