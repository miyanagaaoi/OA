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
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:39.876Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-004`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_dict_item`（§3. 权限）
