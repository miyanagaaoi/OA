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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.658Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
source:
  - path: "doc/forms.md"
    line: 140
    end_line: 140
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
