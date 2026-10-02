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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.674Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
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
