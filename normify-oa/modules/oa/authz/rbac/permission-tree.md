---
uid: 4518af9f
id: oa.authz.rbac.permission-tree
parent: oa.authz.rbac
state: planned
name: {zh: "权限树维护", en: "Permission Tree"}
description:
  zh: >
      权限树定义与层级维护：菜单/按钮/接口三类节点，每节点一个唯一权限码（如 flow:task:approve）与前端路由，支持逐级分配（REQ-ADMIN-003）。
  en: >
      Defines and maintains the permission tree: menu/button/api nodes, each with a unique permission code (e.g. flow:task:approve) and front-end route, assignable level by level.
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:10:00Z"
fingerprint: pending
source:
  - path: "doc/data-model.md"
    line: 219
    end_line: 236
  - path: "doc/prd-0.1.md"
    line: 159
    end_line: 168
apis:
  - protocol: http
    method: GET
    path: "/api/v1/authz/permissions/tree"
    description:
      zh: >
          读取完整权限树。
      en: >
          Reads the full permission tree.
  - protocol: http
    method: POST
    path: "/api/v1/authz/permissions"
    description:
      zh: >
          新增权限节点。
      en: >
          Creates a permission node.
  - protocol: http
    method: PUT
    path: "/api/v1/authz/permissions/{id}"
    description:
      zh: >
          修改权限节点。
      en: >
          Updates a permission node.
  - protocol: http
    method: DELETE
    path: "/api/v1/authz/permissions/{id}"
    description:
      zh: >
          删除权限节点。
      en: >
          Deletes a permission node.
  - protocol: mysql
    path: "sys_permission"
    description:
      zh: >
          权限树表。
      en: >
          Permission table.
deps:
  - kind: reference
    to: oa.design.component
    label: {zh: "侧边树节点组件承载权限树", en: "Permission tree widget"}
---
