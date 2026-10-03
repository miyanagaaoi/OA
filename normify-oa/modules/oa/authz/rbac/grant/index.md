---
uid: "45e81949"
id: oa.authz.rbac.grant
parent: oa.authz.rbac
name: {zh: "角色授权勾选", en: "Role Grant"}
description:
  zh: >
      为角色逐级勾选可访问的功能菜单/按钮与组织节点范围；授权粒度为组织节点×功能，分公司流程管理员仅可在本公司范围内授权（REQ-ADMIN-003）。
      
  en: >
      Ticks, level by level, the功能 menus/buttons and org nodes a role may access; the grant granularity is org node by function, and branch process admins may grant only inside their own company.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:39.955Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-003`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_role_permission`（§3. 权限）
