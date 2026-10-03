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
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.298Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
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
