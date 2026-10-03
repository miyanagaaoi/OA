---
uid: 8f8c431a
id: oa.form.seal.validation
parent: oa.form.seal
state: planned
name: {zh: "印鉴单校验规则", en: "Seal Form Validation"}
description:
  zh: >
      印鉴单专属校验：事由 ≤60、用途说明 ≥5（对外时 ≥20）且 ≤500、用印份数 1–999 整数、使用开始不早于今天、结束 ≥ 开始、seal_type=证照借用 时 cert_name 必填、return_status=已归还 时 return_date 必填；全部在服务端执行。
      
  en: >
      Seal-specific validation: reason ≤60, purpose ≥5 (≥20 when external) and ≤500, copy count an integer 1–999, usage start not earlier than today, end ≥ start, `cert_name` required when seal_type is certificate borrow, and `return_date` required when the status is returned; all server-side.
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.723Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
    line: 157
    end_line: 168
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/seal/validate"
    description:
      zh: >
          印鉴单提交前整体校验。
          
      en: >
          Full pre-submit validation for seal forms.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/seal/validate/cert-conditional"
    description:
      zh: >
          证照借用与归还时间的条件必填校验。
          
      en: >
          Conditional-required checks for certificate borrow and return time.
          
deps:
  - kind: call
    to: oa.form.template.validate
    from_api: "POST /api/v1/forms/seal/validate"
    label: {zh: "复用通用校验引擎", en: "Reuses shared validator"}
---
