---
uid: 8f8c431a
id: oa.form.seal.validation
parent: oa.form.seal
name: {zh: "印鉴单校验规则", en: "Seal Form Validation"}
description:
  zh: >
      印鉴单专属校验：事由 ≤60、用途说明 ≥5（对外时 ≥20）且 ≤500、用印份数 1–999 整数、使用开始不早于今天、结束 ≥ 开始、seal_type=证照借用 时 cert_name 必填、return_status=已归还 时 return_date 必填；全部在服务端执行。
      
  en: >
      Seal-specific validation: reason ≤60, purpose ≥5 (≥20 when external) and ≤500, copy count an integer 1–999, usage start not earlier than today, end ≥ start, `cert_name` required when seal_type is certificate borrow, and `return_date` required when the status is returned; all server-side.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.454Z"
fingerprint: d89254a4a362eeecf74041196101e27dfc2da4a3cda4140ddf808eea93895c61
source:
  - path: "doc/forms.md"
  - path: "oa-server/src/main/java/com/oa/form/seal/SealFormRules.java"
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

## 证据锚点
- `doc/forms.md` → `## 5. 印鉴证照审批单（`form_type = seal`）`（§5. 印鉴证照审批单）
