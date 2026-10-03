---
uid: 1c89b413
id: oa.identity.position.leader-bind
parent: oa.identity.position
name: {zh: "组织负责人绑定", en: "Org Leader Binding"}
description:
  zh: >
      每个部门/科室可绑定一个或多个负责人（正职 primary / 副职 deputy，可排序、可设生效期）；负责人绑定是审批人解析的唯一权威来源。
      
  en: >
      Each department or section may bind one or more leaders (primary/deputy, sortable, with effective dates); leader binding is the single authoritative source for approver resolution.
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.732Z"
fingerprint: 45de2030cdb84b6b065d6ae29d070a0561de3211039af1703055f01a49a9f2e4
source:
  - path: "doc/data-model.md"
    line: 85
    end_line: 108
  - path: "doc/prd-0.1.md"
    line: 137
    end_line: 142
apis:
  - protocol: http
    method: GET
    path: "/api/v1/identity/orgs/{id}/leaders"
    description:
      zh: >
          查询节点的负责人列表。
          
      en: >
          Lists leaders of an org node.
          
  - protocol: http
    method: POST
    path: "/api/v1/identity/orgs/{id}/leaders"
    description:
      zh: >
          新增负责人绑定。
          
      en: >
          Binds a leader.
          
  - protocol: http
    method: PUT
    path: "/api/v1/identity/orgs/{id}/leaders/{leaderId}"
    description:
      zh: >
          调整正副职、排序与生效期。
          
      en: >
          Updates leader type, sort or effective dates.
          
  - protocol: http
    method: DELETE
    path: "/api/v1/identity/orgs/{id}/leaders/{leaderId}"
    description:
      zh: >
          解除负责人绑定。
          
      en: >
          Unbinds a leader.
          
  - protocol: mysql
    path: "sys_org_leader"
    description:
      zh: >
          组织负责人表。
          
      en: >
          Org-leader table.
          
deps:
  - kind: call
    to: oa.identity.user.profile
    from_api: "POST /api/v1/identity/orgs/{id}/leaders"
    to_api: "GET /api/v1/identity/users"
    label: {zh: "负责人须为在职人员", en: "Validate active user"}
  - kind: call
    to: oa.identity.org.node
    from_api: "POST /api/v1/identity/orgs/{id}/leaders"
    to_api: "GET /api/v1/identity/orgs/tree"
    label: {zh: "校验组织节点", en: "Validate org node"}
---
