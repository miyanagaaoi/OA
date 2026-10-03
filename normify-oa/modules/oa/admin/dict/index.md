---
uid: 8a95ac4e
id: oa.admin.dict
parent: oa.admin
state: planned
name: {zh: "数据字典管理", en: "Dictionary Admin"}
description:
  zh: >
      维护事项类别、合同类型、用印类型、证照类型等基础选项；新增选项无需发版即可生效，已被单据引用的选项只能停用不可删除。
      
  en: >
      Maintain the option lists behind request categories, contract types, seal types and certificate types; new options take effect without a release, and in-use items can only be disabled.
      
revision: 966907fad0c5f0d01bc6a76ddba80bbbf67f586a
updated_at: "2026-10-03T04:41:37.251Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-004`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_dict_item`（§3. 权限）
