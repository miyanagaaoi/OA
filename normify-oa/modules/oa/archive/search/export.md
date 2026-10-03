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
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.655Z"
fingerprint: 45de2030cdb84b6b065d6ae29d070a0561de3211039af1703055f01a49a9f2e4
source:
  - path: "doc/prd-0.1.md"
    line: 540
    end_line: 540
  - path: "doc/data-model.md"
    line: 824
    end_line: 824
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
