---
uid: 0f459cb5
id: oa.form.template.validate
parent: oa.form.template
name: {zh: "服务端二次校验", en: "Server-Side Validation"}
description:
  zh: >
      所有必填、长度、金额、日期、文件与条件必填校验必须在服务端执行，前端校验仅为体验；校验按模板 schema 驱动，四类单据共用同一校验引擎。
      
  en: >
      All required, length, amount, date, file and conditional-required checks run server-side; front-end checks are convenience only. Validation is schema-driven and shared by all four document types.
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.367Z"
fingerprint: 1268039d728a92b9049af8f8ef57edfe0120dd3416a1c0784daace7c495c4cb2
source:
  - path: "doc/forms.md"
  - path: "doc/forms.md"
  - path: "oa-server/src/main/java/com/oa/form/template/validate/FormPayloadValidator.java"
  - path: "oa-server/src/main/java/com/oa/form/template/validate/FormValidationReport.java"
  - path: "oa-server/src/main/java/com/oa/form/app/FormSubmitGate.java"
---

## 证据锚点
- `doc/forms.md` → `### 1.3 通用校验规则`（§1.3 通用校验规则）
