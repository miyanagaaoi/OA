---
uid: 9cd9ac34
id: oa.form.print.variant.group-contract
parent: oa.form.print.variant
name: {zh: "集团合同类文件流转审批单", en: "Group Contract Sheet"}
description:
  zh: >
      严格对齐实单结构：抬头、三栏表头、「公文接收及处理」重复块（最多 3 段）、集团领导意见合并栏、收尾行「公文回传 + 印鉴证照管理部门」；实测高度 274mm，余量约 23mm，新增字段需复查单页高度。
      
  en: >
      Mirrors the paper form: heading, three-column header row, the repeated document-receipt block (up to three segments), the merged group-leader opinion cell and the closing row. Measured height 274mm with about 23mm spare, so added fields require a page-height recheck.
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.325Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 970
    end_line: 970
  - path: "DESIGN.md"
    line: 1003
    end_line: 1013
  - path: "DESIGN.md"
    line: 1043
    end_line: 1043
apis:
  - protocol: file
    path: "templates/print/group-contract.html"
    description:
      zh: >
          集团合同类文件流转审批单打印模板。
          
      en: >
          Print template for the group contract circulation sheet.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/print/contract/{instance_id}"
    description:
      zh: >
          渲染合同单的集团版式打印稿。
          
      en: >
          Renders a contract document in the group layout.
          
deps:
  - kind: reference
    to: oa.design.print
    from_api: "GET /api/v1/forms/print/contract/{instance_id}"
    label: {zh: "打印版式规范", en: "Print layout spec"}
---
