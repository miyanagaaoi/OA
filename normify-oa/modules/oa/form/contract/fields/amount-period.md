---
uid: 6ddc6401
id: oa.form.contract.fields.amount-period
parent: oa.form.contract.fields
state: planned
name: {zh: "金额与履约期字段组", en: "Amount & Term Field Group"}
description:
  zh: >
      合同金额 amount（amount、必填、> 0，金额规则见 1.5）、履约开始 period_start（date、必填）、履约结束 period_end（date、必填、≥ period_start）、是否框架合同 is_framework（boolean、必填、默认否）。
  en: >
      Contract amount `amount` (amount, required, > 0 per rule 1.5), performance start `period_start` (date, required), performance end `period_end` (date, required, ≥ start) and framework flag `is_framework` (boolean, required, default no).
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:10:00Z"
fingerprint: pending
source:
  - path: "doc/forms.md"
    line: 136
    end_line: 139
  - path: "doc/forms.md"
    line: 147
    end_line: 147
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
