---
uid: "10421368"
id: oa.form.template.validate.required-length
parent: oa.form.template.validate
state: planned
name: {zh: "必填与长度校验", en: "Required & Length Validation"}
description:
  zh: >
      必填（空字符串与全空格视为空）与长度上限校验：提示「请填写{标签}」「{标签}不能超过 {N} 个字符」；中文按字符计。
      
  en: >
      Required checks (empty string and all-whitespace count as empty) and max-length checks, with the messages 「请填写{标签}」 and 「{标签}不能超过 {N} 个字符」; Chinese counts by character.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.681Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
source:
  - path: "doc/forms.md"
    line: 41
    end_line: 42
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/{form_type}/validate/required"
    description:
      zh: >
          必填校验。
          
      en: >
          Required-field validation.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/{form_type}/validate/length"
    description:
      zh: >
          长度上限校验。
          
      en: >
          Max-length validation.
          
---
