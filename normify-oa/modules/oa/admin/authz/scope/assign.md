---
uid: 690f4178
id: oa.admin.authz.scope.assign
parent: oa.admin.authz.scope
state: planned
name: {zh: "角色数据域设置", en: "Role Data Scope"}
description:
  zh: >
      在五种允许值中设置角色的 data_scope，并保持角色的集团级/公司级属性与之一致。
      
  en: >
      Sets the data_scope value of a role among the five allowed values and keeps the role's group/company level consistent with it.
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.613Z"
fingerprint: 6be246c6834b9b56d4a42d5b955150bb6a06a766a90c031fd7f3af6b4c917a6d
source:
  - path: "doc/data-model.md"
    line: 174
    end_line: 176
  - path: "doc/prd-0.1.md"
    line: 437
    end_line: 437
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
