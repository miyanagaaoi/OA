---
uid: 77885d78
id: oa.form.contract.special.framework-amount
parent: oa.form.contract.special
name: {zh: "框架合同金额与期限", en: "Framework Contract Rules"}
description:
  zh: >
      is_framework=是 时合同金额按上限金额填写、period_end 必填；履约周期用于后续到期提醒（一期仅记录，提醒属 P1 范围）。
      
  en: >
      When `is_framework` is yes the amount is filled as a ceiling and `period_end` becomes mandatory; the performance term feeds later expiry reminders, which phase one merely records (reminders are P1).
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.002Z"
fingerprint: 10c6effd215627f1c844a3acecef0995e0cae341763861c18553845cb71394d6
source:
  - path: "doc/forms.md"
  - path: "doc/forms.md"
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

## 证据锚点
- `doc/forms.md` → `## 4. 合同审批单（`form_type = contract`）`（§4. 合同审批单）
