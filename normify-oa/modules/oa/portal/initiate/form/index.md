---
uid: 1334f2b9
id: oa.portal.initiate.form
parent: oa.portal.initiate
state: planned
name: {zh: "单列表单渲染器", en: "Single-Column Form Renderer"}
description:
  zh: >
      由模板 form_schema_json 驱动的表单渲染器（四类单据共用一套，不在前端硬编码字段）：单列、标签在上、控件占满表单宽度、内容列最大 760px 居中、字段行间距 16px；≥1440px 时右侧出现 140px 本页导航吸附目录。
      
  en: >
      A form renderer driven by the template's form_schema_json, shared by all four document types with no hard-coded fields: single column, label above the control, controls filling the form width, content column capped at 760px and centred, 16px between field rows; from 1440px up a 140px sticky in-page index appears on the right.
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.656Z"
fingerprint: 88c368e042714c6c1aa4b765a97c2bda96929b19c7be8666e8f9f305cf305896
source:
  - path: "DESIGN.md"
    line: 856
    end_line: 871
  - path: "doc/forms.md"
deps:
  - kind: reference
    to: oa.form.template
    label: {zh: "表单由模板字段定义驱动", en: "Rendered from template fields"}
---

## 证据锚点
- `doc/forms.md` → `## 11. 表单模板实现要求`（§11. 表单模板实现要求）
