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
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.244Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-004`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_dict_item`（§3. 权限）
