---
uid: b57e8c4a
id: oa.form.print.field-render.checkbox
parent: oa.form.print.field-render
state: planned
name: {zh: "复选框呈现", en: "Checkbox Rendering"}
description:
  zh: >
      单选/多选字段在打印稿上以文字符号 `☑ / ☐` 呈现（用正文字号），不使用 input[type=checkbox]，保证打印与复印观感一致；plan_category、payment_belong、「其他会审部门」按纸质实单样式呈现。
      
  en: >
      Single and multi-select fields print as the text symbols `☑ / ☐` at body size rather than input[type=checkbox], keeping print and photocopy consistent; plan_category, payment_belong and the other joint-review departments line follow the paper form's look.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.668Z"
fingerprint: ad88e328b04de7a8d2ba605f8fc806c579bcb555ef8cadc76b8edcedbd0695f5
source:
  - path: "DESIGN.md"
    line: 1011
    end_line: 1011
  - path: "doc/forms.md"
    line: 370
    end_line: 370
  - path: "doc/forms.md"
    line: 379
    end_line: 380
apis:
  - protocol: file
    path: "templates/print/partials/checkbox.html"
    description:
      zh: >
          ☑/☐ 复选框呈现片段。
          
      en: >
          Partial rendering ☑/☐ checkboxes.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/print/{instance_id}/checkbox-fields"
    description:
      zh: >
          返回打印稿上以勾选框呈现的字段。
          
      en: >
          Returns the fields rendered as checkboxes on the sheet.
          
---
