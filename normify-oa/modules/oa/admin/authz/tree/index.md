---
uid: 5907126a
id: oa.admin.authz.tree
parent: oa.admin.authz
state: planned
name: {zh: "权限树勾选", en: "Permission Tree Ticking"}
description:
  zh: >
      为角色勾选权限树节点、设置数据域与归口类别绑定、试算可见范围，并将每次变更留痕；IT 部门逐级分配，分公司流程管理员不可再向下分配。
      
  en: >
      Tick permission-tree nodes for a role, set the role's data scope and category bindings, preview the resulting visibility, and log every change; IT assigns node by node and branch admins cannot re-delegate.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.413Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-003`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_permission`（§3. 权限）
