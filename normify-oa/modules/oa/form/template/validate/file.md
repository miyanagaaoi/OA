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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.680Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
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
