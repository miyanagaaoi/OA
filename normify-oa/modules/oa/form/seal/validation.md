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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.676Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
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
