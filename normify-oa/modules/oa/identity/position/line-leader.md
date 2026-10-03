---
uid: 1e4f689e
id: oa.identity.position.line-leader
parent: oa.identity.position
name: {zh: "业务线分管领导", en: "Business-line Leaders"}
description:
  zh: >
      集团层按业务线绑定分管领导（财务/人力/行政/经营等分类）：用于集团分管领导节点的审批人解析，并决定其按业务线的数据可见范围。
      
  en: >
      Group-level leaders are bound per business line (finance, HR, administration, operations): it drives approver resolution for the group-executive node and their business-line data scope.
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.321Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/prd-0.1.md"
    line: 137
    end_line: 142
  - path: "doc/data-model.md"
    line: 85
    end_line: 108
apis:
  - protocol: http
    method: GET
    path: "/api/v1/identity/leaders/lines"
    description:
      zh: >
          查询各业务线分管领导。
          
      en: >
          Lists leaders per business line.
          
  - protocol: http
    method: PUT
    path: "/api/v1/identity/leaders/lines/{category}"
    description:
      zh: >
          设置某业务线的分管领导。
          
      en: >
          Sets the leader of a business line.
          
deps:
  - kind: call
    to: oa.identity.position.leader-bind
    from_api: "PUT /api/v1/identity/leaders/lines/{category}"
    to_api: "POST /api/v1/identity/orgs/{id}/leaders"
    label: {zh: "分管领导落库到组织负责人", en: "Bind line leader"}
  - kind: reference
    to: oa.authz.scope.category
    from_api: "PUT /api/v1/identity/leaders/lines/{category}"
    to_api: "GET /api/v1/authz/categories"
    label: {zh: "业务线取值来自类别口径", en: "Category options source"}
---
