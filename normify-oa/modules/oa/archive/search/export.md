---
uid: 36d4da99
id: oa.archive.search.export
parent: oa.archive.search
state: planned
name: {zh: "历史审计导出", en: "History Audit Export"}
description:
  zh: >
      对归档单据与轨迹发起审计导出，生成年度归档数据文件并记录导出人与范围，导出动作本身留痕。
      
  en: >
      Runs audit exports over archived documents and trails, producing yearly data files while recording exporter and scope; the export action itself is logged.
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.149Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
    line: 540
    end_line: 540
  - path: "doc/data-model.md"
    line: 921
    end_line: 921
apis:
  - protocol: http
    method: POST
    path: "/api/v1/archive/exports"
    description:
      zh: >
          对归档数据发起审计导出。
          
      en: >
          Starts an audit export over archived data.
          
  - protocol: http
    method: GET
    path: "/api/v1/archive/exports/{export_id}"
    description:
      zh: >
          查看导出进度。
          
      en: >
          Reads export progress.
          
  - protocol: file
    path: "export/archive/{yyyy}/documents.csv"
    description:
      zh: >
          按年度生成的归档单据数据文件。
          
      en: >
          Yearly archive document export file.
          
deps:
  - kind: reference
    to: oa.audit.oplog.query
    from_api: "POST /api/v1/archive/exports"
    label: {zh: "导出行为留痕", en: "Log the export action"}
  - kind: reference
    to: oa.admin.boundary
    from_api: "POST /api/v1/archive/exports"
    label: {zh: "导出权限边界", en: "Export permission boundary"}
---
