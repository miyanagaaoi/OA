---
uid: 7f35be6d
id: oa.form.seal.fields.seal
parent: oa.form.seal.fields
state: planned
name: {zh: "用印字段组", en: "Seal Use Field Group"}
description:
  zh: >
      用印类型 seal_type（select、必填、取值见 6.4）、用印份数 seal_count（number、1–999 整数、默认 1、seal_type ≠ 证照借用 时必填）、是否对外提供 is_external（boolean、必填、默认否，为是时用途说明长度下限提升）。
      
  en: >
      Seal type `seal_type` (select, required, values per 6.4), copy count `seal_count` (number, integer 1–999, default 1, required unless the type is certificate borrow) and external flag `is_external` (boolean, required, default no; yes raises the purpose minimum length).
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.693Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
    line: 159
    end_line: 159
  - path: "doc/forms.md"
    line: 164
    end_line: 165
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
