---
uid: 7f3b98c1
id: oa.form.seal.fields.cert
parent: oa.form.seal.fields
name: {zh: "证照名称字段组", en: "Certificate Field Group"}
description:
  zh: >
      证照名称 cert_name（select、条件必填、取值见 6.5）：仅当 seal_type=证照借用 时必填，其余用印类型下隐藏。
      
  en: >
      Certificate name `cert_name` (select, conditionally required, values per 6.5): mandatory only when seal_type is certificate borrow and hidden for the other seal types.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.278Z"
fingerprint: 1266f6407e434b7ee473c37529842b35c2605a39bc4ad8881f6bc19d40d4caf1
source:
  - path: "doc/forms.md"
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

## 证据锚点
- `doc/forms.md` → `## 5. 印鉴证照审批单（`form_type = seal`）`（§5. 印鉴证照审批单）
