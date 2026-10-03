---
uid: 01fcec0f
id: oa.form.template.schema
parent: oa.form.template
name: {zh: "模板 schema 与字段定义", en: "Template Schema & Field Definitions"}
description:
  zh: >
      表单模板 `form_schema_json` 的定义与解析：字段 ID（小写蛇形、全项目唯一、一经使用不得复用）、控件类型、长度、必填、默认值与联动声明，以及四类单据模板的版本与字典绑定。四类单据共用同一套 schema 结构，界面不硬编码字段。
      
  en: >
      Defines and parses `form_schema_json`: field IDs (lower snake_case, globally unique, never reused once used), control types, lengths, required flags, defaults and linkage declarations, plus template versions and dictionary bindings. All four document types share one schema shape and the UI hardcodes no field.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.046Z"
fingerprint: 10c6effd215627f1c844a3acecef0995e0cae341763861c18553845cb71394d6
source:
  - path: "doc/forms.md"
  - path: "doc/forms.md"
---

## 证据锚点
- `doc/forms.md` → `## 11. 表单模板实现要求`（§11. 表单模板实现要求）
