---
uid: 4a093e50
id: oa.authz.rbac.grant.org-node
parent: oa.authz.rbac.grant
state: planned
name: {zh: "组织节点授权范围", en: "Org-node Grant Scope"}
description:
  zh: >
      勾选角色可访问的组织节点子集（如某公司/某部门），与数据域口径共同决定可见范围；不可跨出授权人自身的组织边界。
      
  en: >
      Ticks the subset of org nodes a role may access (a company or department); together with the data scope it fixes the visible range and may never exceed the granter's own org boundary.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.630Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
source:
  - path: "doc/prd-0.1.md"
    line: 159
    end_line: 168
  - path: "doc/data-model.md"
    line: 188
    end_line: 203
apis:
  - protocol: http
    method: GET
    path: "/api/v1/authz/roles/{id}/org-nodes"
    description:
      zh: >
          读取角色可访问的组织节点。
          
      en: >
          Lists org nodes a role may access.
          
  - protocol: http
    method: PUT
    path: "/api/v1/authz/roles/{id}/org-nodes"
    description:
      zh: >
          保存角色的组织节点范围。
          
      en: >
          Saves the role's org-node scope.
          
deps:
  - kind: call
    to: oa.identity.org.node
    from_api: "PUT /api/v1/authz/roles/{id}/org-nodes"
    to_api: "GET /api/v1/identity/orgs/tree"
    label: {zh: "校验组织节点", en: "Validate org node"}
  - kind: call
    to: oa.authz.rbac.role
    from_api: "PUT /api/v1/authz/roles/{id}/org-nodes"
    to_api: "GET /api/v1/authz/roles"
    label: {zh: "校验角色与授权边界", en: "Validate role"}
---
