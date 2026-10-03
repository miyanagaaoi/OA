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
      
revision: c22d447e6e63ccb0edfd9624026f21e8d1413077
updated_at: "2026-10-03T02:20:07.631Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
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
