---
uid: 24157f47
id: oa.workflow.approver.rule-table.org-chain
parent: oa.workflow.approver.rule-table
state: planned
name: {zh: "组织链规则", en: "Org-Chain Rules"}
description:
  zh: >
      直属部门负责人（取发起者科室负责人，科室未设负责人时上溯取所属部门负责人）、分公司分管领导（按发起者所属公司匹配）、子公司总经理（取发起者所属公司总经理）三条规则的解析。
      
  en: >
      Resolves the three org-chain rules: direct department leader (leader of the initiator's section, falling back up to the parent department leader), branch line leader (matched by the initiator's company) and subsidiary GM (GM of the initiator's company).
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.795Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 208
    end_line: 208
  - path: "doc/prd-0.1.md"
    line: 210
    end_line: 211
apis:
  - protocol: http
    method: POST
    path: "/api/v1/approver-rules/dept-leader/resolve"
    description:
      zh: >
          取发起者科室负责人，未设则上溯取所属部门负责人。
          
      en: >
          Resolves the section leader, falling back up to the parent department leader.
          
  - protocol: http
    method: POST
    path: "/api/v1/approver-rules/company-exec/resolve"
    description:
      zh: >
          按发起者所属公司匹配分公司绑定的分管领导。
          
      en: >
          Resolves the branch line leader bound to the initiator's company.
          
  - protocol: http
    method: POST
    path: "/api/v1/approver-rules/gm/resolve"
    description:
      zh: >
          取发起者所属公司的总经理。
          
      en: >
          Resolves the GM of the initiator's company.
          
deps:
  - kind: call
    to: oa.identity.org
    from_api: "POST /api/v1/approver-rules/dept-leader/resolve"
    label: {zh: "取科室/部门负责人", en: "Section/department leaders"}
  - kind: reference
    to: oa.identity.user
    label: {zh: "输出候选人", en: "Candidate users"}
---
