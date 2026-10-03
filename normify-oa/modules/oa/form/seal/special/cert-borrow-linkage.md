---
uid: 9845de3c
id: oa.form.seal.special.cert-borrow-linkage
parent: oa.form.seal.special
name: {zh: "证照借用与份数联动", en: "Certificate Borrow Linkage"}
description:
  zh: >
      seal_type=证照借用 时 cert_name 必填且 seal_count 隐藏；公章/合同章/财务章/法人章四种用印类型下 seal_count 必填（1–999 整数）、cert_name 隐藏。
      
  en: >
      When seal_type is certificate borrow, `cert_name` is required and `seal_count` is hidden; for the four seal types (company/contract/finance/legal) `seal_count` is required (integer 1–999) and `cert_name` is hidden.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.281Z"
fingerprint: ed7ba8e6f66441e00eea24a41f31d51deee3801de9f6747e244a3b2a6886f80e
source:
  - path: "doc/forms.md"
  - path: "doc/forms.md"
  - path: "oa-server/src/main/java/com/oa/form/seal/SealFormRules.java"
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

## 证据锚点
- `doc/forms.md` → `### 6.4 用印类型（字段 code `seal_type` · 字典类型 `seal_type`）`（§6.4 用印类型）
