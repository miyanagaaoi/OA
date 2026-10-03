---
uid: "11953404"
id: oa.form.template.validate.amount
parent: oa.form.template.validate
name: {zh: "金额校验与定点化", en: "Amount Validation"}
description:
  zh: >
      金额统一规则：> 0、最多两位小数、≤ 99,999,999,999.99，以 DECIMAL(18,2) 定点存储，禁止经浮点运算后落库；提示「金额必须大于 0 且最多两位小数」；金额不参与流程路由。
      
  en: >
      Shared amount rules: > 0, at most two decimals, ≤ 99,999,999,999.99, stored as DECIMAL(18,2) fixed point, never persisted after float arithmetic; the error message is 「金额必须大于 0 且最多两位小数」; amounts never drive routing.
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.334Z"
fingerprint: 51c81033e188e0a521d5f073dea3f47f28b869a837de38302f0108709e0ebe3d
source:
  - path: "doc/forms.md"
  - path: "doc/forms.md"
  - path: "doc/forms.md"
  - path: "oa-server/src/main/java/com/oa/form/template/validate/AmountText.java"
  - path: "oa-server/src/main/java/com/oa/form/template/validate/FormPayloadValidator.java"
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

## 证据锚点
- `doc/forms.md` → `### 1.5 金额字段的统一规则（全局）`（§1.5 金额字段的统一规则）
