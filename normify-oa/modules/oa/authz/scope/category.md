---
uid: 73a02658
id: oa.authz.scope.category
parent: oa.authz.scope
state: planned
name: {zh: "归口类别数据域", en: "Category Scope"}
description:
  zh: >
      归口类别口径：事项类别取值由数据字典维护（新增无需发版，REQ-ADMIN-004 的下游消费），角色的类别范围落在角色×类别表；归口统一财务部后该口径仅用于按业务线的角色（如集团分管领导）。
      
  en: >
      Category scope: category values come from the data dictionary (no release needed, downstream consumer of REQ-ADMIN-004) and a role's category range lives in the role-category table; after centralizing on Finance it only applies to business-line roles such as group executives.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.633Z"
fingerprint: a5c8c53b676315cbf9ccdfb80d068b584cf8e89cdb8f4dda5c25769eecec1871
source:
  - path: "doc/data-model.md"
    line: 205
    end_line: 217
  - path: "doc/data-model.md"
    line: 722
    end_line: 735
apis:
  - protocol: http
    method: GET
    path: "/api/v1/authz/categories"
    description:
      zh: >
          读取事项类别选项（字典下游消费）。
          
      en: >
          Lists category options from the dictionary.
          
  - protocol: http
    method: GET
    path: "/api/v1/authz/roles/{id}/categories"
    description:
      zh: >
          读取角色的可见类别集合。
          
      en: >
          Lists the categories visible to a role.
          
  - protocol: http
    method: PUT
    path: "/api/v1/authz/roles/{id}/categories"
    description:
      zh: >
          设置角色的可见类别集合。
          
      en: >
          Sets the categories visible to a role.
          
  - protocol: mysql
    path: "sys_role_category"
    description:
      zh: >
          角色可见事项类别表。
          
      en: >
          Role-category table.
          
deps:
  - kind: reference
    to: oa.admin.dict.item.crud
    from_api: "GET /api/v1/authz/categories"
    to_api: "mysql:sys_dict_item"
    label: {zh: "类别取值由数据字典维护", en: "Category options source"}
---
