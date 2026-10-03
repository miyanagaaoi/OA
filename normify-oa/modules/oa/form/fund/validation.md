---
uid: 4568196d
id: oa.form.fund.validation
parent: oa.form.fund
name: {zh: "资金单校验规则", en: "Fund Form Validation"}
description:
  zh: >
      资金单专属校验：申请金额必填且 > 0（0 或空禁止提交）、收款方名称与账号必填及账号字符集、支付日期不早于今天、关联合同单号有效性、附件 ≥1；全部在服务端执行。
      
  en: >
      Fund-specific validation: the amount is required and > 0 (zero or empty blocks submission), payee name and account are required with a valid account character set, the pay date is not earlier than today, the linked contract number must be valid, and at least one attachment is required; all server-side.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:58:34.400Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
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

## 证据锚点
- `doc/forms.md` → `## 3. 资金审批单（`form_type = fund`）`（§3. 资金审批单）
