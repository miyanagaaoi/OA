---
uid: 40fa60d4
id: oa.authz.rbac.role
parent: oa.authz.rbac
state: planned
name: {zh: "角色定义与分类", en: "Role Definition"}
description:
  zh: >
      角色主数据：编码（admin/company_admin/employee/dept_leader/gm/group_dept_leader/group_exec/chairman）、名称、集团级或公司级范围与默认数据域口径，是授权与数据域判定的入口（REQ-AUTH-001）。
      
  en: >
      Role master data: code (admin/company_admin/employee/dept_leader/gm/group_dept_leader/group_exec/chairman), name, group or company level and default data scope — the entry point for grants and scope decisions.
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.692Z"
fingerprint: 1ebf4aee4ac8648bfaec0ea0afbfecc1b3e2f5fecc0dff532be914d9e85be0f2
source:
  - path: "doc/data-model.md"
    line: 164
    end_line: 186
  - path: "doc/prd-0.1.md"
    line: 159
    end_line: 168
apis:
  - protocol: http
    method: GET
    path: "/api/v1/authz/roles"
    description:
      zh: >
          查询角色列表。
          
      en: >
          Lists roles.
          
  - protocol: http
    method: POST
    path: "/api/v1/authz/roles"
    description:
      zh: >
          新建角色。
          
      en: >
          Creates a role.
          
  - protocol: http
    method: PUT
    path: "/api/v1/authz/roles/{id}"
    description:
      zh: >
          修改角色名称与范围。
          
      en: >
          Updates role name and scope.
          
  - protocol: http
    method: DELETE
    path: "/api/v1/authz/roles/{id}"
    description:
      zh: >
          删除角色（无用户关联时）。
          
      en: >
          Deletes a role when unassigned.
          
  - protocol: mysql
    path: "sys_role"
    description:
      zh: >
          角色表。
          
      en: >
          Role table.
          
deps:
  - kind: reference
    to: oa.authz.scope.catalog
    from_api: "POST /api/v1/authz/roles"
    to_api: "GET /api/v1/authz/data-scopes"
    label: {zh: "默认数据域取口径取值", en: "Default data scope value"}
---
