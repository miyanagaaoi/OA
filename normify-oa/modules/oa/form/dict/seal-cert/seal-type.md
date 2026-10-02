---
uid: 594f65d8
id: oa.form.dict.seal-cert.seal-type
parent: oa.form.dict.seal-cert
state: planned
name: {zh: "用印类型字典", en: "Seal Type Dictionary"}
description:
  zh: >
      用印类型 seal_type：company_seal 公章 / contract_seal 合同章 / finance_seal 财务章 / legal_seal 法人章（以上需填用印份数）；cert_borrow 证照借用（必须选 `cert_name`、无份数）。合同审批单的拟用印类型共用本字典。
      
  en: >
      Seal type `seal_type`: company seal, contract seal, finance seal and legal seal (all require a copy count); certificate borrow (requires `cert_name`, no count). The contract form's intended seal type shares this dictionary.
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.699Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
    line: 215
    end_line: 223
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/dicts/seal-type/items"
    description:
      zh: >
          用印类型可选值列表。
          
      en: >
          Lists selectable seal types.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/dicts/seal-type/{code}/requires-count"
    description:
      zh: >
          判断该类型是否需填用印份数。
          
      en: >
          Tells whether the type needs a copy count.
          
---
