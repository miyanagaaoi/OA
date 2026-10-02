---
uid: 4568196d
id: oa.form.fund.validation
parent: oa.form.fund
state: planned
name: {zh: "资金单校验规则", en: "Fund Form Validation"}
description:
  zh: >
      资金单专属校验：申请金额必填且 > 0（0 或空禁止提交）、收款方名称与账号必填及账号字符集、支付日期不早于今天、关联合同单号有效性、附件 ≥1；全部在服务端执行。
      
  en: >
      Fund-specific validation: the amount is required and > 0 (zero or empty blocks submission), payee name and account are required with a valid account character set, the pay date is not earlier than today, the linked contract number must be valid, and at least one attachment is required; all server-side.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.663Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
source:
  - path: "doc/forms.md"
    line: 106
    end_line: 113
  - path: "doc/forms.md"
    line: 117
    end_line: 117
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/fund/validate"
    description:
      zh: >
          资金单提交前整体校验。
          
      en: >
          Full pre-submit validation for fund forms.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/fund/validate/amount-required"
    description:
      zh: >
          申请金额必填与精度校验。
          
      en: >
          Validates requiredness and precision of the amount.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/fund/validate/attachments"
    description:
      zh: >
          校验附件不少于 1 个。
          
      en: >
          Validates at least one attachment is present.
          
deps:
  - kind: call
    to: oa.form.template.validate
    from_api: "POST /api/v1/forms/fund/validate"
    label: {zh: "复用通用校验引擎", en: "Reuses shared validator"}
---
