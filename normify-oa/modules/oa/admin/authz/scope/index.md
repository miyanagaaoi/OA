---
uid: 5ba09bef
id: oa.admin.authz.scope
parent: oa.admin.authz
state: planned
name: {zh: "数据域配置", en: "Data Scope Admin"}
description:
  zh: >
      配置每个角色的数据域（本人/本部门/本公司/全集团/全集团按归口类别），为按类别取数的角色绑定事项类别，并试算可见范围。
      
  en: >
      Configure each role's data scope (self, department, company, group-wide, group-wide by category), bind the matter categories used by category-scoped roles, and simulate the resulting visibility.
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.479Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-003`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_role`（§3. 权限）
