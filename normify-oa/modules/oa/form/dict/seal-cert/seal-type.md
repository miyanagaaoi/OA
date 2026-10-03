---
uid: 594f65d8
id: oa.form.dict.seal-cert.seal-type
parent: oa.form.dict.seal-cert
name: {zh: "用印类型字典", en: "Seal Type Dictionary"}
description:
  zh: >
      用印类型 seal_type：company_seal 公章 / contract_seal 合同章 / finance_seal 财务章 / legal_seal 法人章（以上需填用印份数）；cert_borrow 证照借用（必须选 `cert_name`、无份数）。合同审批单的拟用印类型共用本字典。
      
  en: >
      Seal type `seal_type`: company seal, contract seal, finance seal and legal seal (all require a copy count); certificate borrow (requires `cert_name`, no count). The contract form's intended seal type shares this dictionary.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.264Z"
fingerprint: 1266f6407e434b7ee473c37529842b35c2605a39bc4ad8881f6bc19d40d4caf1
source:
  - path: "doc/forms.md"
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

## 证据锚点
- `doc/forms.md` → `### 6.4 用印类型（字段 code `seal_type` · 字典类型 `seal_type`）`（§6.4 用印类型）
