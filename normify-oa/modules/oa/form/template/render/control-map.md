---
uid: 0e7e2429
id: oa.form.template.render.control-map
parent: oa.form.template.render
name: {zh: "控件映射渲染", en: "Control Mapping"}
description:
  zh: >
      字段类型到界面控件的映射与渲染：text/textarea/number/amount/select/multiselect/date/daterange/user/org/tag/boolean/file/files；金额控件等宽右对齐，附件控件复用通用上传限制。
      
  en: >
      Maps field types to UI controls and renders them: text/textarea/number/amount/select/multiselect/date/daterange/user/org/tag/boolean/file/files; amount controls are monospaced and right-aligned, file controls reuse the shared upload rules.
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.331Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/render/{form_type}"
    description:
      zh: >
          按模板渲染表单结构（字段与控件）。
          
      en: >
          Renders the form structure (fields and controls) from a template.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/render/control-map"
    description:
      zh: >
          字段类型到控件的映射清单。
          
      en: >
          Lists field-type to control mappings.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/instances/{instance_id}/draft"
    description:
      zh: >
          读取草稿数据用于渲染。
          
      en: >
          Reads draft data for rendering.
          
---

## 证据锚点
- `doc/forms.md` → `## 11. 表单模板实现要求`（§11. 表单模板实现要求）
