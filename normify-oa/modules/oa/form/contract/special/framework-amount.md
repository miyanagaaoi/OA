---
uid: 77885d78
id: oa.form.contract.special.framework-amount
parent: oa.form.contract.special
state: planned
name: {zh: "框架合同金额与期限", en: "Framework Contract Rules"}
description:
  zh: >
      is_framework=是 时合同金额按上限金额填写、period_end 必填；履约周期用于后续到期提醒（一期仅记录，提醒属 P1 范围）。
      
  en: >
      When `is_framework` is yes the amount is filled as a ceiling and `period_end` becomes mandatory; the performance term feeds later expiry reminders, which phase one merely records (reminders are P1).
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.657Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
source:
  - path: "doc/forms.md"
    line: 139
    end_line: 139
  - path: "doc/forms.md"
    line: 147
    end_line: 147
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/contract/fields/is-framework/evaluate"
    description:
      zh: >
          求值框架合同的金额与期限约束。
          
      en: >
          Evaluates amount and term constraints for framework contracts.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/contract/fields/is-framework"
    description:
      zh: >
          读取是否框架合同及其约束。
          
      en: >
          Reads the framework flag and its constraints.
          
---
