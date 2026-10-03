---
uid: 7e2e47b5
id: oa.admin.dict.item.order
parent: oa.admin.dict.item
name: {zh: "排序与启停", en: "Item Order & State"}
description:
  zh: >
      管理字典项的排序与启停状态，并在停用前给出引用量，供管理员判断停用是否安全。
      
  en: >
      Manages the sort order and active state of dictionary items and reports reference counts so an operator can judge whether disabling an option is safe.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.379Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/data-model.md"
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/data-model.md` → `CREATE TABLE sys_dict_item`（§3. 权限）
- `doc/prd-0.1.md` → `REQ-ADMIN-004`（§6.10 管理后台）
