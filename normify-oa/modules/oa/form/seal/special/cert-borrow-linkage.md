---
uid: 9845de3c
id: oa.form.seal.special.cert-borrow-linkage
parent: oa.form.seal.special
state: planned
name: {zh: "证照借用与份数联动", en: "Certificate Borrow Linkage"}
description:
  zh: >
      seal_type=证照借用 时 cert_name 必填且 seal_count 隐藏；公章/合同章/财务章/法人章四种用印类型下 seal_count 必填（1–999 整数）、cert_name 隐藏。
      
  en: >
      When seal_type is certificate borrow, `cert_name` is required and `seal_count` is hidden; for the four seal types (company/contract/finance/legal) `seal_count` is required (integer 1–999) and `cert_name` is hidden.
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.693Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
    line: 159
    end_line: 160
  - path: "doc/forms.md"
    line: 164
    end_line: 164
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/seal/fields/seal-type/linkage"
    description:
      zh: >
          求值证照借用与份数的显示/必填联动。
          
      en: >
          Evaluates visibility and requiredness linkage for certificate borrow and count.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/seal/fields/seal-type/requirements"
    description:
      zh: >
          读取用印类型的字段要求。
          
      en: >
          Reads field requirements per seal type.
          
deps:
  - kind: call
    to: oa.form.template.render.linkage
    from_api: "POST /api/v1/forms/seal/fields/seal-type/linkage"
    to_api: "POST /api/v1/forms/render/{form_type}/linkage"
    label: {zh: "复用联动求值", en: "Reuses linkage evaluation"}
---
