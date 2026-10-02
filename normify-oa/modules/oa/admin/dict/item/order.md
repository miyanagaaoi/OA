---
uid: 7e2e47b5
id: oa.admin.dict.item.order
parent: oa.admin.dict.item
state: planned
name: {zh: "排序与启停", en: "Item Order & State"}
description:
  zh: >
      管理字典项的排序与启停状态，并在停用前给出引用量，供管理员判断停用是否安全。
      
  en: >
      Manages the sort order and active state of dictionary items and reports reference counts so an operator can judge whether disabling an option is safe.
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.663Z"
fingerprint: 1ebf4aee4ac8648bfaec0ea0afbfecc1b3e2f5fecc0dff532be914d9e85be0f2
source:
  - path: "doc/data-model.md"
    line: 261
    end_line: 262
  - path: "doc/prd-0.1.md"
    line: 438
    end_line: 438
apis:
  - protocol: http
    method: PUT
    path: "/api/v1/admin/dict-items/{item_id}/status"
    description:
      zh: >
          启用/停用字典项（不删除历史数据）。
          
      en: >
          Enable or disable an item without deleting history.
          
  - protocol: http
    method: PUT
    path: "/api/v1/admin/dict-items/sort"
    description:
      zh: >
          批量调整字典项排序。
          
      en: >
          Bulk adjust the display order of items.
          
  - protocol: http
    method: GET
    path: "/api/v1/admin/dict-items/{item_id}/usage"
    description:
      zh: >
          停用前统计字典项的引用量。
          
      en: >
          Count references before disabling an item.
          
deps:
  - kind: reference
    to: oa.form.dict
    from_api: "GET /api/v1/admin/dict-items/{item_id}/usage"
    label: {zh: "表单侧引用校验", en: "Reference check on forms"}
---
