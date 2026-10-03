---
uid: 4a093e50
id: oa.authz.rbac.grant.org-node
parent: oa.authz.rbac.grant
name: {zh: "组织节点授权范围", en: "Org-node Grant Scope"}
description:
  zh: >
      勾选角色可访问的组织节点子集（如某公司/某部门），与数据域口径共同决定可见范围；不可跨出授权人自身的组织边界。
      
  en: >
      Ticks the subset of org nodes a role may access (a company or department); together with the data scope it fixes the visible range and may never exceed the granter's own org boundary.
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.181Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
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
