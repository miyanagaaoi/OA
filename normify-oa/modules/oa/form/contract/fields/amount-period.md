---
uid: 6ddc6401
id: oa.form.contract.fields.amount-period
parent: oa.form.contract.fields
name: {zh: "金额与履约期字段组", en: "Amount & Term Field Group"}
description:
  zh: >
      合同金额 amount（amount、必填、> 0，金额规则见 1.5）、履约开始 period_start（date、必填）、履约结束 period_end（date、必填、≥ period_start）、是否框架合同 is_framework（boolean、必填、默认否）。
      
  en: >
      Contract amount `amount` (amount, required, > 0 per rule 1.5), performance start `period_start` (date, required), performance end `period_end` (date, required, ≥ start) and framework flag `is_framework` (boolean, required, default no).
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.429Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/contract/field-groups/amount-period"
    description:
      zh: >
          金额与履约期字段组定义。
          
      en: >
          Amount and performance-term field group definition.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/contract/fields/period/validate"
    description:
      zh: >
          校验履约期结束 ≥ 开始。
          
      en: >
          Validates that the term's end is ≥ its start.
          
---

## 证据锚点
- `doc/forms.md` → `## 4. 合同审批单（`form_type = contract`）`（§4. 合同审批单）
