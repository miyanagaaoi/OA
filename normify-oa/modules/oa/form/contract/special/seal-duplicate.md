---
uid: 78496f60
id: oa.form.contract.special.seal-duplicate
parent: oa.form.contract.special
state: planned
name: {zh: "重复用印提示与印鉴联动", en: "Duplicate Seal Warning"}
description:
  zh: >
      拟用印类型与印鉴证照单联动：按合同名称、对方主体与金额比对已通过的用印记录，同一合同重复用印时给出提示（不阻断提交，供审批人参考）。
      
  en: >
      The intended seal type links to the seal & certificate form: contract name, counterparty and amount are compared against approved seal records, and repeated seal use on the same contract raises a warning (non-blocking, for approvers' reference).
      
revision: 44fc7aba1c7e884ffa3553faf31bfce974b388a9
updated_at: "2026-10-03T04:12:20.702Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/contract/fields/sign-seal-type/duplicate-check"
    description:
      zh: >
          检查同一合同的重复用印。
          
      en: >
          Checks for repeated seal use on the same contract.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/contract/instances/{instance_id}/seal-usage"
    description:
      zh: >
          读取该合同的用印记录。
          
      en: >
          Reads the contract's seal usage records.
          
deps:
  - kind: call
    to: oa.form.seal.fields.seal
    from_api: "POST /api/v1/forms/contract/fields/sign-seal-type/duplicate-check"
    label: {zh: "用印记录比对", en: "Compare seal usage"}
---

## 证据锚点
- `doc/forms.md` → `## 4. 合同审批单（`form_type = contract`）`（§4. 合同审批单）
