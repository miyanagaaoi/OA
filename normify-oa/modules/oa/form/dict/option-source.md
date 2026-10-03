---
uid: 4a307d4e
id: oa.form.dict.option-source
parent: oa.form.dict
state: planned
name: {zh: "字典项来源与后台维护", en: "Dictionary Item Source"}
description:
  zh: >
      字典项统一由管理后台维护并存储在 `sys_dict_item`（REQ-ADMIN-004），新增选项不需发版；表单侧读取启用项并缓存，后台变更后失效缓存。
      
  en: >
      Dictionary items are maintained in the admin console and stored in `sys_dict_item` (REQ-ADMIN-004); new options need no release. Forms read enabled items through a cache that is invalidated when the console changes them.
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.276Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
    line: 178
    end_line: 178
  - path: "doc/forms.md"
    line: 182
    end_line: 182
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/dicts/{dict_key}/items"
    description:
      zh: >
          读取字典的启用项列表。
          
      en: >
          Reads a dictionary's enabled items.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/dicts/{dict_key}/items/{code}"
    description:
      zh: >
          读取单个字典项（code 与名称）。
          
      en: >
          Reads a single dictionary item (code and name).
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/dicts/cache/refresh"
    description:
      zh: >
          字典缓存失效与重建。
          
      en: >
          Invalidates and rebuilds the dictionary cache.
          
deps:
  - kind: call
    to: oa.admin.dict
    from_api: "GET /api/v1/forms/dicts/{dict_key}/items"
    label: {zh: "后台字典维护", en: "Admin dictionary upkeep"}
  - kind: dataflow
    to: oa.admin.dict.item.crud
    from_api: "GET /api/v1/forms/dicts/{dict_key}/items"
    to_api: "mysql:sys_dict_item"
    label: {zh: "读取字典项", en: "Read dictionary items"}
---
