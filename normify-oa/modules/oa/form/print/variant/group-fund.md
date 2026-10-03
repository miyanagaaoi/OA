---
uid: 9d2ecec0
id: oa.form.print.variant.group-fund
parent: oa.form.print.variant
state: planned
name: {zh: "集团资金审批单", en: "Group Fund Sheet"}
description:
  zh: >
      严格对齐实单：三栏表头、多轮签名栏（集团职能部门/集团分管领导/集团董事长，各含「签名：____ 年 月 日」）、收尾行「系统关联」（关联单号 + 审批链）；实测高度 242mm；打印稿保留实单标签「集团职能部门」而不随系统术语改为财务部。
      
  en: >
      Mirrors the paper form: three-column header, multi-section signature blocks (group function department / group executives / chairman, each with a blank signature line), and the closing system-linkage row (linked document number and approval chain). Measured height 242mm; the paper label 「集团职能部门」 is kept rather than renamed to finance.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.348Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 971
    end_line: 971
  - path: "DESIGN.md"
    line: 1008
    end_line: 1010
  - path: "DESIGN.md"
    line: 1037
    end_line: 1037
  - path: "DESIGN.md"
    line: 1044
    end_line: 1044
apis:
  - protocol: file
    path: "templates/print/group-fund.html"
    description:
      zh: >
          集团资金审批单打印模板。
          
      en: >
          Print template for the group fund sheet.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/print/fund/{instance_id}"
    description:
      zh: >
          渲染资金单的集团版式打印稿。
          
      en: >
          Renders a fund document in the group layout.
          
---
