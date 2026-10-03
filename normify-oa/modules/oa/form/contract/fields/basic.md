---
uid: 6956e5f3
id: oa.form.contract.fields.basic
parent: oa.form.contract.fields
name: {zh: "合同基础字段组", en: "Contract Basic Field Group"}
description:
  zh: >
      合同基础信息字段：合同名称 title（text ≤80，必填）、事项类别 category（固定为经营 business，默认且置灰）、合同类型 contract_type（select，见 forms.md 6.3）与其他类型说明 contract_type_other（text ≤40，类型为「其他」时必填）。
      
  en: >
      Contract basics: contract name `title` (text ≤80, required), category `category` (fixed to business, defaulted and greyed out), contract type `contract_type` (select per section 6.3) and other-type note `contract_type_other` (text ≤40, required when the type is other).
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.258Z"
fingerprint: 1266f6407e434b7ee473c37529842b35c2605a39bc4ad8881f6bc19d40d4caf1
source:
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/contract/field-groups/basic"
    description:
      zh: >
          合同单基础字段组定义。
          
      en: >
          Basic field group definition for contract forms.
          
  - protocol: http
    method: PUT
    path: "/api/v1/forms/contract/instances/{instance_id}/draft/basic"
    description:
      zh: >
          保存合同单基础字段草稿。
          
      en: >
          Saves the contract basic field group draft.
          
deps:
  - kind: reference
    to: oa.form.dict.contract-type
    from_api: "GET /api/v1/forms/contract/field-groups/basic"
    to_api: "GET /api/v1/forms/dicts/contract-type/items"
    label: {zh: "合同类型取值来源", en: "Contract type options"}
---

## 证据锚点
- `doc/forms.md` → `## 4. 合同审批单（`form_type = contract`）`（§4. 合同审批单）
