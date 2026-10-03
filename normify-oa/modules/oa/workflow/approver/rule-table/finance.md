---
uid: 253f3558
id: oa.workflow.approver.rule-table.finance
parent: oa.workflow.approver.rule-table
name: {zh: "财务部归口规则", en: "Finance Ownership Rule"}
description:
  zh: >
      节点②财务部复核 = 集团归口部门：五个事项类别统一归口，恒取集团财务部负责人，不按类别分流；即使事项审批单“不涉及费用”跳过该节点，归口部门字段仍记为财务部（Q11+Q12）。
      
  en: >
      Node ② (Finance review) is the group owning department: all five matter categories route to Finance, so it always resolves the group Finance leader instead of splitting by category; even when a matter form skips the node (no cost involved) the owning department is still recorded as Finance (Q11+Q12).
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.359Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/approver-rules/finance-leader/resolve"
    description:
      zh: >
          恒取集团财务部负责人；五个事项分类不细分归口。
          
      en: >
          Always resolves the group Finance department leader; categories never split the target.
          
  - protocol: http
    method: GET
    path: "/api/v1/approver-rules/finance-leader/health"
    description:
      zh: >
          财务部负责人空缺预警（否则发起将被拦截）。
          
      en: >
          Warns when the group Finance leader post is vacant.
          
deps:
  - kind: call
    to: oa.identity.org
    from_api: "POST /api/v1/approver-rules/finance-leader/resolve"
    label: {zh: "取集团财务部负责人", en: "Group Finance leader"}
  - kind: reference
    to: oa.identity.user
    label: {zh: "输出候选人", en: "Candidate users"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-001`（§6.3.1 集团层流转机制）
