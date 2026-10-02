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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.620Z"
fingerprint: 4421092e639ef98e9cd4aac53b5975054268b604256d140e3544a8a3c2ba5551
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
