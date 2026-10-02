---
uid: 79ca5b85
id: oa.admin.dict.item.crud
parent: oa.admin.dict.item
state: planned
name: {zh: "字典项增删改", en: "Dictionary Item CRUD"}
description:
  zh: >
      以 dict_type + item_code 为键增删改字典项；一旦被单据引用即拒绝删除，只能停用。
      
  en: >
      Creates, updates and deletes dictionary items keyed by dict_type plus item_code, refusing deletion once an item is referenced by any document.
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.662Z"
fingerprint: 1ebf4aee4ac8648bfaec0ea0afbfecc1b3e2f5fecc0dff532be914d9e85be0f2
source:
  - path: "doc/prd-0.1.md"
    line: 438
    end_line: 438
  - path: "doc/data-model.md"
    line: 256
    end_line: 267
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/dict-items"
    description:
      zh: >
          按类型查询字典项。
          
      en: >
          List dictionary items by type.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/dict-items"
    description:
      zh: >
          新增字典项。
          
      en: >
          Create an item under a dictionary type.
          
  - protocol: http
    method: PUT
    path: "/api/v1/admin/dict-items/{item_id}"
    description:
      zh: >
          修改字典项编码、名称与说明。
          
      en: >
          Update item code, name and remark.
          
  - protocol: http
    method: DELETE
    path: "/api/v1/admin/dict-items/{item_id}"
    description:
      zh: >
          删除未被引用的字典项；已被引用则拒绝。
          
      en: >
          Delete an unused item; in-use items are refused.
          
  - protocol: mysql
    path: "sys_dict_item"
    description:
      zh: >
          数据字典表（类型/编码/名称/排序/状态）。
          
      en: >
          Dictionary item table (type/code/name/sort/status).
          
---
