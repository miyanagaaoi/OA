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
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.347Z"
fingerprint: 3677ada712f976a13af7d0b59d7ca50765b031d91b5f9dea79eb02e1f375eeff
source:
  - path: "doc/forms.md"
  - path: "oa-server/src/main/java/com/oa/form/fund/FundFormRules.java"
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
