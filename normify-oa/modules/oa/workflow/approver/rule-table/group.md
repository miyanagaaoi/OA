---
uid: 2b1aea97
id: oa.workflow.approver.rule-table.group
parent: oa.workflow.approver.rule-table
state: planned
name: {zh: "集团层规则", en: "Group-Layer Rules"}
description:
  zh: >
      集团分管领导（按事项类别匹配集团层绑定的分管领导，类别为快照不可改判）与集团董事长（唯一候选人）两条规则的解析；类别仅作分类标签、不参与路由（REQ-FLOW-001）。
      
  en: >
      Resolves the group line leader (matched to the category snapshot bound at group level) and the chairman (single candidate); the category is a configuration label only and never affects routing (REQ-FLOW-001).
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.764Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 213
    end_line: 214
apis:
  - protocol: http
    method: POST
    path: "/api/v1/approver-rules/group-exec/resolve"
    description:
      zh: >
          按事项类别快照匹配集团层绑定的分管领导（类别只作标签不参与路由）。
          
      en: >
          Matches the group line leader by the category snapshot (label only, never routing).
          
  - protocol: http
    method: POST
    path: "/api/v1/approver-rules/chairman/resolve"
    description:
      zh: >
          取集团董事长（唯一候选人）。
          
      en: >
          Resolves the group chairman as the single candidate.
          
deps:
  - kind: call
    to: oa.identity.org
    label: {zh: "取分管领导与董事长", en: "Line leader & chairman"}
  - kind: dataflow
    to: oa.form.dict
    label: {zh: "事项类别取字典", en: "Category from dictionary"}
---
