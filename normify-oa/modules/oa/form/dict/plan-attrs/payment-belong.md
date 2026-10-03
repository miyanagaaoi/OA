---
uid: 63d76b36
id: oa.form.dict.plan-attrs.payment-belong
parent: oa.form.dict.plan-attrs
name: {zh: "付款归属字典", en: "Payment Belong Dictionary"}
description:
  zh: >
      付款归属取值：current_month 本月度（默认勾选）/ current_year 本年度 / prior_year 以前年度；打印稿按纸质实单写法呈现为三选一，一期不用于账龄或预算执行率统计。
      
  en: >
      Payment belong values: current_month (default), current_year and prior_year; the print sheet presents them as the paper form's three-way choice, and phase one uses them for no aging or budget-execution statistics.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.008Z"
fingerprint: 10c6effd215627f1c844a3acecef0995e0cae341763861c18553845cb71394d6
source:
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/dicts/payment-belong/items"
    description:
      zh: >
          付款归属可选值（含默认勾选）。
          
      en: >
          Lists payment belong options including the default.
          
---

## 证据锚点
- `doc/forms.md` → `### 6.8 付款归属 `payment_belong`（**非字典项**：布尔 checkbox，一期仅存储）`（§6.8 付款归属 `payment_belong…）
