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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.687Z"
fingerprint: 88c368e042714c6c1aa4b765a97c2bda96929b19c7be8666e8f9f305cf305896
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
