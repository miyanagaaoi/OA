---
uid: 79b346bb
id: oa.admin.dict.delivery
parent: oa.admin.dict
name: {zh: "字典分发", en: "Dictionary Delivery"}
description:
  zh: >
      把字典变更分发到运行中的系统：缓存刷新让新增选项无需发版即可生效，并提供整表导入导出。
      
  en: >
      Propagates dictionary changes to the running system: cache refresh so new options are usable without a release, plus spreadsheet import and export of the whole dictionary.
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.253Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-004`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_dict_item`（§3. 权限）
