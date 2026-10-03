---
uid: 1d19285a
id: oa.identity.position.multi-post
parent: oa.identity.position
name: {zh: "一人多岗任职", en: "Multi-post Assignment"}
description:
  zh: >
      一名员工可同时挂职于多个组织节点：任职记录含主岗标记与职务名，(user_id, org_id) 唯一；多岗是审批人解析与数据域取最宽口径的输入。
      
  en: >
      One employee may hold posts in several org nodes: each record has a primary flag and position name, unique per (user, org); multi-post feeds approver resolution and the widest-scope rule.
      
revision: c22d447e6e63ccb0edfd9624026f21e8d1413077
updated_at: "2026-10-03T02:20:07.662Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/data-model.md"
    line: 110
    end_line: 126
  - path: "doc/prd-0.1.md"
    line: 133
    end_line: 142
apis:
  - protocol: http
    method: GET
    path: "/api/v1/identity/users/{id}/positions"
    description:
      zh: >
          查询员工全部任职节点。
          
      en: >
          Lists all posts of a user.
          
  - protocol: http
    method: POST
    path: "/api/v1/identity/users/{id}/positions"
    description:
      zh: >
          新增任职（含主岗标记）。
          
      en: >
          Adds a post with its primary flag.
          
  - protocol: http
    method: DELETE
    path: "/api/v1/identity/users/{id}/positions/{positionId}"
    description:
      zh: >
          解除一条任职。
          
      en: >
          Removes a post.
          
  - protocol: mysql
    path: "sys_user_position"
    description:
      zh: >
          岗位任职表。
          
      en: >
          User-position table.
          
deps:
  - kind: call
    to: oa.identity.user.profile
    from_api: "POST /api/v1/identity/users/{id}/positions"
    to_api: "GET /api/v1/identity/users"
    label: {zh: "校验在职人员", en: "Validate user"}
  - kind: call
    to: oa.identity.org.node
    from_api: "POST /api/v1/identity/users/{id}/positions"
    to_api: "GET /api/v1/identity/orgs/tree"
    label: {zh: "校验任职组织节点", en: "Validate org node"}
---
