---
uid: 72f1a156
id: oa.admin.dict.type
parent: oa.admin.dict
state: planned
name: {zh: "字典类型管理", en: "Dictionary Types"}
description:
  zh: >
      维护字典类型目录（category/pay_method/contract_type/seal_type/cert_name 等），并提供恢复内置默认选项的能力。
      
  en: >
      Maintains the dictionary type catalogue (category, pay_method, contract_type, seal_type, cert_name and friends) and provides a reset back to the built-in defaults.
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.194Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/prd-0.1.md"
    line: 438
    end_line: 438
  - path: "doc/data-model.md"
    line: 258
    end_line: 258
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
