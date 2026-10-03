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
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.363Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/data-model.md"
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/data-model.md` → `CREATE TABLE sys_user_position`（§2. 身份与组织）
- `doc/prd-0.1.md` → `REQ-ORG-001`（§5.1 组织架构模型）
