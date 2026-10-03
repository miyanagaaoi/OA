---
uid: 5ba09bef
id: oa.admin.authz.scope
parent: oa.admin.authz
name: {zh: "数据域配置", en: "Data Scope Admin"}
description:
  zh: >
      配置每个角色的数据域（本人/本部门/本公司/全集团/全集团按归口类别），为按类别取数的角色绑定事项类别，并试算可见范围。
      
  en: >
      Configure each role's data scope (self, department, company, group-wide, group-wide by category), bind the matter categories used by category-scoped roles, and simulate the resulting visibility.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.175Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-003`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_role`（§3. 权限）
