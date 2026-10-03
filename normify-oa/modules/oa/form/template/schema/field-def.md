---
uid: 07f675b0
id: oa.form.template.schema.field-def
parent: oa.form.template.schema
name: {zh: "字段定义结构", en: "Field Definition Structure"}
description:
  zh: >
      单个字段在 schema 中的定义项：字段 ID、标签、类型（text/textarea/number/amount/select/multiselect/date/daterange/user/org/tag/boolean/file/files）、必填（是/否/条件必填）、长度、校验、发起后可改性、默认值与联动。字段 code 一经使用不得复用。
      
  en: >
      One field's schema entry: field ID, label, type (text/textarea/number/amount/select/multiselect/date/daterange/user/org/tag/boolean/file/files), required (yes/no/conditional), length, validation, post-submit mutability, default and linkage. A field code is never reused once used.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.460Z"
fingerprint: 57d876d9a5ac43c22cbd3224ed2253b35a5d64dabc2dba6c922439c9d5144960
source:
  - path: "doc/forms.md"
  - path: "oa-server/src/main/java/com/oa/form/template/schema/FormFieldDef.java"
  - path: "oa-server/src/main/java/com/oa/form/template/schema/FormFieldType.java"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/templates/{form_type}/schema"
    description:
      zh: >
          读取模板的字段定义列表。
          
      en: >
          Reads a template's field definitions.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/templates/{form_type}/schema/fields"
    description:
      zh: >
          新增字段定义，校验字段 ID 为小写蛇形且全项目唯一。
          
      en: >
          Adds a field definition, validating lower snake_case and global uniqueness.
          
  - protocol: http
    method: PATCH
    path: "/api/v1/forms/templates/{form_type}/schema/fields/{field_id}"
    description:
      zh: >
          修改字段定义（字段 ID 本身不可改）。
          
      en: >
          Updates a field definition; the field ID itself is immutable.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/field-types"
    description:
      zh: >
          字段类型与控件清单。
          
      en: >
          Lists field types and their controls.
          
---

## 证据锚点
- `doc/forms.md` → `### 1.1 字段属性含义`（§1.1 字段属性含义）
