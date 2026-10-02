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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.661Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
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
