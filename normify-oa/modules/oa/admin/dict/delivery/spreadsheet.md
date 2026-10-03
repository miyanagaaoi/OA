---
uid: 822926b5
id: oa.admin.dict.delivery.spreadsheet
parent: oa.admin.dict.delivery
name: {zh: "字典导入导出", en: "Dictionary Spreadsheet"}
description:
  zh: >
      以 Excel 导入导出全量字典，按 dict_type + item_code 幂等，重复导入不会产生重复选项。
      
  en: >
      Imports and exports the whole dictionary through Excel, keyed idempotently by dict_type plus item_code so repeated imports do not duplicate options.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.184Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: file
    path: "import/dict-items.xlsx"
    description:
      zh: >
          字典项导入模板文件。
          
      en: >
          Dictionary item import template file.
          
  - protocol: file
    path: "export/dict-items.xlsx"
    description:
      zh: >
          全量字典导出文件。
          
      en: >
          Full dictionary export file.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/dict-items/import"
    description:
      zh: >
          按类型+编码幂等的批量导入。
          
      en: >
          Idempotent bulk import keyed by type and code.
          
  - protocol: http
    method: GET
    path: "/api/v1/admin/dict-items/export"
    description:
      zh: >
          导出全部字典类型与选项。
          
      en: >
          Export all dictionary types and items.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-004`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_dict_item`（§3. 权限）
