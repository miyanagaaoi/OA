---
uid: 753a4f21
id: oa.admin.dict.item
parent: oa.admin.dict
state: planned
name: {zh: "字典项维护", en: "Dictionary Items"}
description:
  zh: >
      字典项增删改、排序与启停，以及导入导出；已被引用的选项不可删除，新增选项无需发版即可生效。
      
  en: >
      CRUD over dictionary items, ordering and enable/disable, plus import/export; in-use items cannot be deleted and new options take effect without a release.
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.490Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-004`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_dict_item`（§3. 权限）
