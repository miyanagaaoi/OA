---
uid: "11953404"
id: oa.form.template.validate.amount
parent: oa.form.template.validate
state: planned
name: {zh: "金额校验与定点化", en: "Amount Validation"}
description:
  zh: >
      金额统一规则：> 0、最多两位小数、≤ 99,999,999,999.99，以 DECIMAL(18,2) 定点存储，禁止经浮点运算后落库；提示「金额必须大于 0 且最多两位小数」；金额不参与流程路由。
      
  en: >
      Shared amount rules: > 0, at most two decimals, ≤ 99,999,999,999.99, stored as DECIMAL(18,2) fixed point, never persisted after float arithmetic; the error message is 「金额必须大于 0 且最多两位小数」; amounts never drive routing.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.680Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
source:
  - path: "doc/forms.md"
    line: 43
    end_line: 43
  - path: "doc/forms.md"
    line: 61
    end_line: 69
  - path: "doc/forms.md"
    line: 401
    end_line: 401
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/{form_type}/validate/amount"
    description:
      zh: >
          金额范围与精度校验。
          
      en: >
          Validates amount range and precision.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/amount/normalize"
    description:
      zh: >
          金额字符串定点归一化（禁止浮点）。
          
      en: >
          Normalizes an amount string to fixed point (no floats).
          
---
