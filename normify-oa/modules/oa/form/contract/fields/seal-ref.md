---
uid: 6ef8ed8c
id: oa.form.contract.fields.seal-ref
parent: oa.form.contract.fields
state: planned
name: {zh: "拟用印类型字段组", en: "Intended Seal Type Group"}
description:
  zh: >
      拟用印类型 sign_seal_type（select、必填、取值见 6.4、默认合同章）；与印鉴证照单联动，同一合同重复用印需提示。
      
  en: >
      Intended seal type `sign_seal_type` (select, required, values per 6.4, defaults to contract seal); it links to the seal & certificate form and warns about repeated seal use on the same contract.
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.203Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
    line: 145
    end_line: 145
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/contract/field-groups/seal-ref"
    description:
      zh: >
          拟用印类型字段组定义。
          
      en: >
          Intended seal type field group definition.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/contract/fields/sign-seal-type/options"
    description:
      zh: >
          拟用印类型可选值（默认合同章）。
          
      en: >
          Selectable intended seal types (contract seal by default).
          
deps:
  - kind: reference
    to: oa.form.dict.seal-cert.seal-type
    from_api: "GET /api/v1/forms/contract/fields/sign-seal-type/options"
    to_api: "GET /api/v1/forms/dicts/seal-type/items"
    label: {zh: "用印类型取值来源", en: "Seal type options"}
---
