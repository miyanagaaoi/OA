---
uid: 8a95ac4e
id: oa.admin.dict
parent: oa.admin
name: {zh: "数据字典管理", en: "Dictionary Admin"}
description:
  zh: >
      维护事项类别、合同类型、用印类型、证照类型等基础选项；新增选项无需发版即可生效，已被单据引用的选项只能停用不可删除。
      
  en: >
      Maintain the option lists behind request categories, contract types, seal types and certificate types; new options take effect without a release, and in-use items can only be disabled.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.184Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-004`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_dict_item`（§3. 权限）
