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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.594Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
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
