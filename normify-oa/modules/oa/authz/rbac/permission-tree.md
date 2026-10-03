---
uid: 4518af9f
id: oa.authz.rbac.permission-tree
parent: oa.authz.rbac
name: {zh: "权限树维护", en: "Permission Tree"}
description:
  zh: >
      权限树定义与层级维护：菜单/按钮/接口三类节点，每节点一个唯一权限码（如 flow:task:approve）与前端路由，支持逐级分配（REQ-ADMIN-003）。
      
  en: >
      Defines and maintains the permission tree: menu/button/api nodes, each with a unique permission code (e.g. flow:task:approve) and front-end route, assignable level by level.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.237Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/data-model.md"
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/data-model.md` → `CREATE TABLE sys_permission`（§3. 权限）
- `doc/prd-0.1.md` → `REQ-ADMIN-003`（§6.10 管理后台）
