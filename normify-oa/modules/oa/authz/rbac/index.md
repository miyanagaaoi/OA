---
uid: 1c2e4d33
id: oa.authz.rbac
parent: oa.authz
name: {zh: "角色与权限树", en: "Roles & Permission Tree"}
description:
  zh: >
      角色定义与权限树（菜单/功能节点）及角色-权限分配；IT 部门在后台逐级勾选可访问组织节点与功能菜单；分公司流程管理员可配本公司模板与人员但不可再向下分配权限。
      
  en: >
      Roles and the permission tree (menu/feature nodes) plus role-permission assignment; IT assigns permissions node by node, and branch process admins may maintain their own company but cannot re-delegate permissions.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.476Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-AUTH-001`（§5.2 权限模型）
- `doc/data-model.md` → `CREATE TABLE sys_role_permission`（§3. 权限）
