---
uid: 72f1a156
id: oa.admin.dict.type
parent: oa.admin.dict
name: {zh: "字典类型管理", en: "Dictionary Types"}
description:
  zh: >
      维护字典类型目录（category/pay_method/contract_type/seal_type/cert_name 等），并提供恢复内置默认选项的能力。
      
  en: >
      Maintains the dictionary type catalogue (category, pay_method, contract_type, seal_type, cert_name and friends) and provides a reset back to the built-in defaults.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.379Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/dict-types"
    description:
      zh: >
          查询字典类型及其选项数量。
          
      en: >
          List dictionary types with item counts.
          
  - protocol: http
    method: PUT
    path: "/api/v1/admin/dict-types/{dict_type}"
    description:
      zh: >
          维护类型元信息（名称、说明、是否系统内置）。
          
      en: >
          Maintain type metadata (name, remark, built-in flag).
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/dict-types/{dict_type}/reset"
    description:
      zh: >
          恢复内置类型的默认选项。
          
      en: >
          Restore the built-in default items of a type.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-004`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_dict_item`（§3. 权限）
