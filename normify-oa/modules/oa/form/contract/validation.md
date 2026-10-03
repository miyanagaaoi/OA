---
uid: "75341544"
id: oa.form.contract.validation
parent: oa.form.contract
state: planned
name: {zh: "合同单校验规则", en: "Contract Form Validation"}
description:
  zh: >
      合同单专属校验：合同名称 ≤80、统一社会信用代码 18 位数字与大写字母、合同金额 > 0、履约结束 ≥ 开始、contract_type=其他 时说明必填、合同文本附件 ≥1；全部在服务端执行。
      
  en: >
      Contract-specific validation: name ≤80, credit code of 18 digits/uppercase letters, amount > 0, term end ≥ start, the note required when the type is other, and at least one contract-text attachment; all server-side.
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.275Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
    line: 130
    end_line: 142
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/contract/validate"
    description:
      zh: >
          合同单提交前整体校验。
          
      en: >
          Full pre-submit validation for contract forms.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/contract/validate/credit-code"
    description:
      zh: >
          统一社会信用代码格式校验。
          
      en: >
          Validates the credit code format.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/contract/validate/term"
    description:
      zh: >
          履约周期与框架合同金额校验。
          
      en: >
          Validates the performance term and framework amount.
          
deps:
  - kind: call
    to: oa.form.template.validate
    from_api: "POST /api/v1/forms/contract/validate"
    label: {zh: "复用通用校验引擎", en: "Reuses shared validator"}
---
