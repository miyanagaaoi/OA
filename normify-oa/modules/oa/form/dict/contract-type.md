---
uid: 51cb6861
id: oa.form.dict.contract-type
parent: oa.form.dict
state: planned
name: {zh: "合同类型字典", en: "Contract Type Dictionary"}
description:
  zh: >
      合同类型 contract_type：purchase 采购 / sales 销售 / service 服务 / lease 租赁 / construction 工程 / labor 劳务 / other 其他（其他时需填 contract_type_other 说明）。
      
  en: >
      Contract type `contract_type`: purchase, sales, service, lease, construction, labor and other (other requires the `contract_type_other` note).
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.675Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
    line: 203
    end_line: 213
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/dicts/contract-type/items"
    description:
      zh: >
          合同类型可选值列表。
          
      en: >
          Lists selectable contract types.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/dicts/contract-type/{code}/requires-note"
    description:
      zh: >
          判断该类型是否需填其他类型说明。
          
      en: >
          Tells whether the type requires an explanatory note.
          
---
