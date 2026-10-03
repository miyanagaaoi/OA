---
uid: 753a4f21
id: oa.admin.dict.item
parent: oa.admin.dict
name: {zh: "字典项维护", en: "Dictionary Items"}
description:
  zh: >
      字典项增删改、排序与启停，以及导入导出；已被引用的选项不可删除，新增选项无需发版即可生效。
      
  en: >
      CRUD over dictionary items, ordering and enable/disable, plus import/export; in-use items cannot be deleted and new options take effect without a release.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.378Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-004`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_dict_item`（§3. 权限）
