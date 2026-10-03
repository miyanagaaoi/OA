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
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.160Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-003`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_role_org_node`（§3. 权限）
