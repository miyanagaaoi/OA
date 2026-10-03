---
uid: 822926b5
id: oa.admin.dict.delivery.spreadsheet
parent: oa.admin.dict.delivery
state: planned
name: {zh: "字典导入导出", en: "Dictionary Spreadsheet"}
description:
  zh: >
      以 Excel 导入导出全量字典，按 dict_type + item_code 幂等，重复导入不会产生重复选项。
      
  en: >
      Imports and exports the whole dictionary through Excel, keyed idempotently by dict_type plus item_code so repeated imports do not duplicate options.
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.621Z"
fingerprint: 45de2030cdb84b6b065d6ae29d070a0561de3211039af1703055f01a49a9f2e4
source:
  - path: "doc/prd-0.1.md"
    line: 438
    end_line: 438
  - path: "doc/data-model.md"
    line: 256
    end_line: 267
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
