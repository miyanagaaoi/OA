---
uid: 7b524e04
id: oa.authz.visibility.field.amount
parent: oa.authz.visibility.field
state: planned
name: {zh: "金额字段只读", en: "Amount Field Read-only"}
description:
  zh: >
      合同金额与资金金额对非财务类角色只读展示、不可导出；**系统管理员与财务角色可导出且导出行为留痕**（V0.4）；判定不依赖字段级配置，一期为硬编码规则。
      
  en: >
      Contract and fund amounts are read-only for non-finance roles and cannot be exported; Finance roles and system administrators may export, and every export is logged. The rule is hard-coded in phase one and does not depend on field-level configuration.
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.656Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 192
    end_line: 199
  - path: "doc/prd-0.1.md"
    line: 546
    end_line: 561
apis:
  - protocol: http
    method: GET
    path: "/api/v1/authz/field-policy/amount"
    description:
      zh: >
          读取金额字段对当前角色的读写策略。
          
      en: >
          Reads the amount policy of the caller's roles.
          
  - protocol: rpc
    path: "authz.field.isAmountReadonly"
    description:
      zh: >
          判定指定角色集下金额是否只读。
          
      en: >
          Tells whether amounts are read-only for given roles.
          
deps:
  - kind: reference
    to: oa.form.fund
    label: {zh: "资金金额字段定义", en: "Fund amount fields"}
  - kind: reference
    to: oa.form.contract
    label: {zh: "合同金额字段定义", en: "Contract amount fields"}
---
