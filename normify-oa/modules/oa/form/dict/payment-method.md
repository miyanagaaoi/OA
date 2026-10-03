---
uid: 4dfd2605
id: oa.form.dict.payment-method
parent: oa.form.dict
name: {zh: "支付方式字典", en: "Payment Method Dictionary"}
description:
  zh: >
      支付方式 pay_method：transfer 银行转账 / acceptance 银行承兑汇票 / cash 现金 / other 其他；供资金审批单的支付方式字段使用。
      
  en: >
      Payment method `pay_method`: transfer, acceptance (bank acceptance bill), cash and other; used by the fund approval form's payment-method field.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.435Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
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

## 证据锚点
- `doc/forms.md` → `### 6.2 支付方式（字段 code `pay_method` · 字典类型 `payment_method`）`（§6.2 支付方式）
