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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.659Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
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
