---
uid: 4dfd2605
id: oa.form.dict.payment-method
parent: oa.form.dict
state: planned
name: {zh: "支付方式字典", en: "Payment Method Dictionary"}
description:
  zh: >
      支付方式 pay_method：transfer 银行转账 / acceptance 银行承兑汇票 / cash 现金 / other 其他；供资金审批单的支付方式字段使用。
  en: >
      Payment method `pay_method`: transfer, acceptance (bank acceptance bill), cash and other; used by the fund approval form's payment-method field.
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:10:00Z"
fingerprint: pending
source:
  - path: "doc/forms.md"
    line: 194
    end_line: 201
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/dicts/pay-method/items"
    description:
      zh: >
          支付方式可选值列表。
      en: >
          Lists selectable payment methods.
---
