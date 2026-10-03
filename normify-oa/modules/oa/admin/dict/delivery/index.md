---
uid: 79b346bb
id: oa.admin.dict.delivery
parent: oa.admin.dict
state: planned
name: {zh: "字典分发", en: "Dictionary Delivery"}
description:
  zh: >
      把字典变更分发到运行中的系统：缓存刷新让新增选项无需发版即可生效，并提供整表导入导出。
      
  en: >
      Propagates dictionary changes to the running system: cache refresh so new options are usable without a release, plus spreadsheet import and export of the whole dictionary.
      
revision: 966907fad0c5f0d01bc6a76ddba80bbbf67f586a
updated_at: "2026-10-03T04:41:37.250Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-004`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_dict_item`（§3. 权限）
