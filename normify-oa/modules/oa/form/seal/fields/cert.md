---
uid: 7f3b98c1
id: oa.form.seal.fields.cert
parent: oa.form.seal.fields
state: planned
name: {zh: "证照名称字段组", en: "Certificate Field Group"}
description:
  zh: >
      证照名称 cert_name（select、条件必填、取值见 6.5）：仅当 seal_type=证照借用 时必填，其余用印类型下隐藏。
      
  en: >
      Certificate name `cert_name` (select, conditionally required, values per 6.5): mandatory only when seal_type is certificate borrow and hidden for the other seal types.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.674Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
source:
  - path: "doc/forms.md"
    line: 160
    end_line: 160
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/seal/field-groups/cert"
    description:
      zh: >
          证照名称字段组定义。
          
      en: >
          Certificate field group definition.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/seal/fields/cert-name/options"
    description:
      zh: >
          证照名称可选值。
          
      en: >
          Selectable certificate names.
          
deps:
  - kind: reference
    to: oa.form.dict.seal-cert.cert-name
    from_api: "GET /api/v1/forms/seal/fields/cert-name/options"
    to_api: "GET /api/v1/forms/dicts/cert-name/items"
    label: {zh: "证照名称取值来源", en: "Certificate options"}
---
