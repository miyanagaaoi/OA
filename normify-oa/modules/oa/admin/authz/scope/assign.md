---
uid: 690f4178
id: oa.admin.authz.scope.assign
parent: oa.admin.authz.scope
name: {zh: "角色数据域设置", en: "Role Data Scope"}
description:
  zh: >
      在五种允许值中设置角色的 data_scope，并保持角色的集团级/公司级属性与之一致。
      
  en: >
      Sets the data_scope value of a role among the five allowed values and keeps the role's group/company level consistent with it.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:39.865Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/data-model.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/roles/{role_id}/data-scope"
    description:
      zh: >
          查询角色的数据域配置。
          
      en: >
          Read the data scope configured for a role.
          
  - protocol: http
    method: PUT
    path: "/api/v1/admin/roles/{role_id}/data-scope"
    description:
      zh: >
          设置角色数据域（本人→全集团）。
          
      en: >
          Set the role data scope (self to group-wide).
          
deps:
  - kind: call
    to: oa.authz.scope
    from_api: "PUT /api/v1/admin/roles/{role_id}/data-scope"
    label: {zh: "数据域口径由权限域执行", en: "Data scope runs in authz"}
  - kind: dataflow
    to: oa.authz.rbac.role
    to_api: "mysql:sys_role"
    label: {zh: "写入角色数据域", en: "Write role data scope"}
---

## 证据锚点
- `doc/data-model.md` → `CREATE TABLE sys_role`（§3. 权限）
- `doc/prd-0.1.md` → `REQ-ADMIN-003`（§6.10 管理后台）
