---
uid: 7b524e04
id: oa.authz.visibility.field.amount
parent: oa.authz.visibility.field
name: {zh: "金额字段只读", en: "Amount Field Read-only"}
description:
  zh: >
      合同金额与资金金额对非财务类角色只读展示、不可导出；**系统管理员与财务角色可导出且导出行为留痕**（V0.4）；判定不依赖字段级配置，一期为硬编码规则。
      
  en: >
      Contract and fund amounts are read-only for non-finance roles and cannot be exported; Finance roles and system administrators may export, and every export is logged. The rule is hard-coded in phase one and does not depend on field-level configuration.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.288Z"
fingerprint: 29146e055831a1c7fe631ae245079de418d031c8e860cc4c8bf2fc2f84fb48f6
source:
  - path: "oa-server/src/main/java/com/oa/authz/visibility/AmountFieldPolicy.java"
  - path: "oa-server/src/main/java/com/oa/authz/visibility/FormFieldWriteGuard.java"
  - path: "oa-server/src/main/java/com/oa/authz/api/FieldPolicyController.java"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/authz/field-policy/amount"
    description:
      zh: >
          读取金额字段对当前角色的读写策略。
          
      en: >
          Reads the amount policy of the caller's roles.
          
  - protocol: http
    method: POST
    path: "/api/v1/authz/field-policy/assert-write"
    description:
      zh: >
          写闸门：按「状态白名单 ∧ 角色金额规则」判定本次表单写入是否放行。
          
      en: >
          Write gate: state whitelist AND role rule decide whether this form write is allowed.
          
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
