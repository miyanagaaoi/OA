---
uid: 426503a2
id: oa.form.fund.fields.payment
parent: oa.form.fund.fields
state: planned
name: {zh: "支付字段组", en: "Payment Field Group"}
description:
  zh: >
      支付方式 pay_method（select、见 6.2）、计划支付日期 pay_date（date、不早于今天）、关联合同单号 contract_ref（text≤40、存在时必须为已通过单据号）、是否加急 urgent（boolean、默认否、为是时启用加急标识与提醒频率提升）。
      
  en: >
      Payment method `pay_method` (select per 6.2), planned pay date `pay_date` (date not earlier than today), linked contract number `contract_ref` (text ≤40, must be an approved document number when present) and urgency `urgent` (boolean, defaults to no; yes enables the urgent marker and higher reminder frequency).
      
revision: c22d447e6e63ccb0edfd9624026f21e8d1413077
updated_at: "2026-10-03T02:20:07.633Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
    line: 109
    end_line: 112
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/fund/field-groups/payment"
    description:
      zh: >
          支付字段组定义。
          
      en: >
          Payment field group definition.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/fund/fields/contract-ref/verify"
    description:
      zh: >
          校验关联合同单号为已通过单据。
          
      en: >
          Verifies the linked contract number is an approved document.
          
deps:
  - kind: call
    to: oa.form.contract
    from_api: "POST /api/v1/forms/fund/fields/contract-ref/verify"
    label: {zh: "合同单号校验来源", en: "Contract number source"}
---
