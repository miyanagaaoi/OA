---
uid: 63d76b36
id: oa.form.dict.plan-attrs.payment-belong
parent: oa.form.dict.plan-attrs
state: planned
name: {zh: "付款归属字典", en: "Payment Belong Dictionary"}
description:
  zh: >
      付款归属取值：current_month 本月度（默认勾选）/ current_year 本年度 / prior_year 以前年度；打印稿按纸质实单写法呈现为三选一，一期不用于账龄或预算执行率统计。
      
  en: >
      Payment belong values: current_month (default), current_year and prior_year; the print sheet presents them as the paper form's three-way choice, and phase one uses them for no aging or budget-execution statistics.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.660Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
source:
  - path: "doc/forms.md"
    line: 252
    end_line: 260
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
