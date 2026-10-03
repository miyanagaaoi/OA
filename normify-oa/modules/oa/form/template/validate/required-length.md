---
uid: "10421368"
id: oa.form.template.validate.required-length
parent: oa.form.template.validate
name: {zh: "必填与长度校验", en: "Required & Length Validation"}
description:
  zh: >
      必填（空字符串与全空格视为空）与长度上限校验：提示「请填写{标签}」「{标签}不能超过 {N} 个字符」；中文按字符计。
      
  en: >
      Required checks (empty string and all-whitespace count as empty) and max-length checks, with the messages 「请填写{标签}」 and 「{标签}不能超过 {N} 个字符」; Chinese counts by character.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.293Z"
fingerprint: dd81a2916ac59f65631a920cf9129c0af6d47a60fafeb8c75da9be2392107064
source:
  - path: "doc/forms.md"
  - path: "oa-server/src/main/java/com/oa/form/template/validate/FormPayloadValidator.java"
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

## 证据锚点
- `doc/forms.md` → `### 1.3 通用校验规则`（§1.3 通用校验规则）
