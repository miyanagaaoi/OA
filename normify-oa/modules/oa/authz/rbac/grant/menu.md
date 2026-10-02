---
uid: "48889032"
id: oa.authz.rbac.grant.menu
parent: oa.authz.rbac.grant
state: planned
name: {zh: "功能菜单授权", en: "Menu Permission Grant"}
description:
  zh: >
      按权限树逐级勾选角色的菜单/按钮/接口权限码（父节点连带子节点），可批量授予与回收；变更写入权限变更日志。
  en: >
      Ticks a role's menu/button/api permission codes level by level in the permission tree (parents drag children) with bulk grant and revoke; every change is written to the permission change log.
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:10:00Z"
fingerprint: pending
source:
  - path: "doc/data-model.md"
    line: 238
    end_line: 251
  - path: "doc/prd-0.1.md"
    line: 433
    end_line: 440
apis:
  - protocol: http
    method: GET
    path: "/api/v1/authz/roles/{id}/permissions"
    description:
      zh: >
          读取角色已勾选的权限码。
      en: >
          Lists granted permission codes of a role.
  - protocol: http
    method: PUT
    path: "/api/v1/authz/roles/{id}/permissions"
    description:
      zh: >
          保存角色的权限码勾选。
      en: >
          Saves the role's permission ticks.
  - protocol: mysql
    path: "sys_role_permission"
    description:
      zh: >
          角色权限表。
      en: >
          Role-permission table.
deps:
  - kind: call
    to: oa.authz.rbac.permission-tree
    from_api: "PUT /api/v1/authz/roles/{id}/permissions"
    to_api: "GET /api/v1/authz/permissions/tree"
    label: {zh: "校验权限码存在", en: "Read permission tree"}
  - kind: call
    to: oa.authz.rbac.role
    from_api: "PUT /api/v1/authz/roles/{id}/permissions"
    to_api: "GET /api/v1/authz/roles"
    label: {zh: "校验角色与授权边界", en: "Validate role"}
---
