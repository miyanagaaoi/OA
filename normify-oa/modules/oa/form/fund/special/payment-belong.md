---
uid: 48d27eb1
id: oa.form.fund.special.payment-belong
parent: oa.form.fund.special
state: planned
name: {zh: "付款归属（一期只存不用）", en: "Payment Belong (Store Only)"}
description:
  zh: >
      付款归属 payment_belong（checkbox、取值见 6.8：本月度/本年度/以前年度、默认勾选本月度）；一期只存储数据，不参与任何流程判断，打印稿按纸质实单写法呈现；二期用于账龄与预算执行率统计。
      
  en: >
      Payment belong `payment_belong` (checkbox per 6.8: current month/year/prior years, defaults to current month); phase one stores it without flow logic and prints it in the paper form's wording; phase two uses it for aging and budget-execution statistics.
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.715Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
    line: 105
    end_line: 105
  - path: "doc/forms.md"
    line: 119
    end_line: 120
  - path: "doc/forms.md"
    line: 252
    end_line: 260
  - path: "doc/forms.md"
    line: 297
    end_line: 297
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/fund/fields/payment-belong"
    description:
      zh: >
          读取付款归属字段与默认勾选。
          
      en: >
          Reads the payment belong field and its default.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/fund/fields/payment-belong/assert-storage-only"
    description:
      zh: >
          校验该字段未被任何流程规则引用。
          
      en: >
          Asserts the field is referenced by no flow rule.
          
deps:
  - kind: reference
    to: oa.form.dict.plan-attrs
    from_api: "GET /api/v1/forms/fund/fields/payment-belong"
    label: {zh: "字典取值来源", en: "Option source"}
---
