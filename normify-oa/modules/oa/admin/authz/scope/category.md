---
uid: 69afb9af
id: oa.admin.authz.scope.category
parent: oa.admin.authz.scope
state: planned
name: {zh: "归口类别绑定", en: "Category Binding"}
description:
  zh: >
      为按归口类别取数的角色绑定可见事项类别，类别选项取自共享字典以保证与表单编码一致。
      
  en: >
      Binds the matter categories that a group_category-scoped role may see, taking the category options from the shared dictionary so codes stay consistent with the forms.
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.108Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/data-model.md"
    line: 209
    end_line: 217
  - path: "doc/prd-0.1.md"
    line: 180
    end_line: 180
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/roles/{role_id}/categories"
    description:
      zh: >
          查询角色绑定的事项类别。
          
      en: >
          List the matter categories bound to a role.
          
  - protocol: http
    method: PUT
    path: "/api/v1/admin/roles/{role_id}/categories"
    description:
      zh: >
          为按类别取数的角色绑定事项类别。
          
      en: >
          Bind matter categories for category-scoped roles.
          
deps:
  - kind: call
    to: oa.admin.dict
    label: {zh: "事项类别取自数据字典", en: "Categories from dictionary"}
  - kind: dataflow
    to: oa.authz.scope.category
    to_api: "mysql:sys_role_category"
    label: {zh: "写入归口类别绑定", en: "Write category binding"}
---
