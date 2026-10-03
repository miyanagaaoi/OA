---
uid: 11b054e6
id: oa.form.template.validate.file
parent: oa.form.template.validate
name: {zh: "文件字段校验", en: "File Field Validation"}
description:
  zh: >
      file / files 字段的格式、大小与数量校验，提示「{标签}仅支持 {格式}，单个文件不超过 {N}MB」；规则复用附件通用限制。
      
  en: >
      Validates format, size and count for file / files fields with the message 「{标签}仅支持 {格式}，单个文件不超过 {N}MB」; the rules reuse the shared attachment limits.
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.366Z"
fingerprint: b61603d361ccc3e3c05b44179a97c2c38b0f1c198a2891672392b4af0f6a647e
source:
  - path: "doc/forms.md"
  - path: "oa-server/src/main/java/com/oa/form/template/validate/FormPayloadValidator.java"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/{form_type}/validate/files"
    description:
      zh: >
          文件字段格式/大小/数量校验。
          
      en: >
          Validates file fields' format, size and count.
          
deps:
  - kind: call
    to: oa.form.template.attachment.upload-policy
    from_api: "POST /api/v1/forms/{form_type}/validate/files"
    label: {zh: "复用附件通用限制", en: "Reuses attachment limits"}
---

## 证据锚点
- `doc/forms.md` → `### 1.3 通用校验规则`（§1.3 通用校验规则）
