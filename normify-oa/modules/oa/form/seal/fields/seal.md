---
uid: 7f35be6d
id: oa.form.seal.fields.seal
parent: oa.form.seal.fields
name: {zh: "用印字段组", en: "Seal Use Field Group"}
description:
  zh: >
      用印类型 seal_type（select、必填、取值见 6.4）、用印份数 seal_count（number、1–999 整数、默认 1、seal_type ≠ 证照借用 时必填）、是否对外提供 is_external（boolean、必填、默认否，为是时用途说明长度下限提升）。
      
  en: >
      Seal type `seal_type` (select, required, values per 6.4), copy count `seal_count` (number, integer 1–999, default 1, required unless the type is certificate borrow) and external flag `is_external` (boolean, required, default no; yes raises the purpose minimum length).
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.279Z"
fingerprint: 1266f6407e434b7ee473c37529842b35c2605a39bc4ad8881f6bc19d40d4caf1
source:
  - path: "doc/forms.md"
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/seal/field-groups/seal"
    description:
      zh: >
          用印字段组定义。
          
      en: >
          Seal-use field group definition.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/seal/fields/seal-count/validate"
    description:
      zh: >
          校验用印份数为 1–999 整数。
          
      en: >
          Validates the copy count as an integer from 1 to 999.
          
deps:
  - kind: reference
    to: oa.form.dict.seal-cert.seal-type
    from_api: "GET /api/v1/forms/seal/field-groups/seal"
    to_api: "GET /api/v1/forms/dicts/seal-type/items"
    label: {zh: "用印类型取值来源", en: "Seal type options"}
---

## 证据锚点
- `doc/forms.md` → `## 5. 印鉴证照审批单（`form_type = seal`）`（§5. 印鉴证照审批单）
