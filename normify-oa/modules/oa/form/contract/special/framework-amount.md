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
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.259Z"
fingerprint: 1266f6407e434b7ee473c37529842b35c2605a39bc4ad8881f6bc19d40d4caf1
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
