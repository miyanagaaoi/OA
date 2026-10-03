---
uid: 0ce72e0e
id: oa.form.template.render
parent: oa.form.template
state: planned
name: {zh: "字段驱动渲染", en: "Schema-Driven Rendering"}
description:
  zh: >
      表单由 `flow_template.form_schema_json` 驱动渲染：类型到控件的映射与字段联动求值（显示/必填/取值）。前端不硬编码字段，四类单据共用一套渲染器。
      
  en: >
      Renders forms from `flow_template.form_schema_json`: type-to-control mapping and field linkage evaluation (visibility, requiredness, value derivation). The front end hardcodes no fields; all four document types share one renderer.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.353Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
  - path: "doc/forms.md"
---

## 证据锚点
- `doc/forms.md` → `## 11. 表单模板实现要求`（§11. 表单模板实现要求）
