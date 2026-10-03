---
uid: 11b054e6
id: oa.form.template.validate.file
parent: oa.form.template.validate
state: planned
name: {zh: "文件字段校验", en: "File Field Validation"}
description:
  zh: >
      file / files 字段的格式、大小与数量校验，提示「{标签}仅支持 {格式}，单个文件不超过 {N}MB」；规则复用附件通用限制。
      
  en: >
      Validates format, size and count for file / files fields with the message 「{标签}仅支持 {格式}，单个文件不超过 {N}MB」; the rules reuse the shared attachment limits.
      
revision: e3b34a3c59417096ade647fab4261b06f6b605e2
updated_at: "2026-10-03T02:30:28.719Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
    line: 46
    end_line: 46
  - path: "doc/forms.md"
    line: 49
    end_line: 59
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
