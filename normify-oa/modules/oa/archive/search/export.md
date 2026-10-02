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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.616Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
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
