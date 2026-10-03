---
uid: a31eb2fc
id: oa.form.print.page
parent: oa.form.print
state: planned
name: {zh: "纸张版心与分页", en: "Paper, Margins & Pagination"}
description:
  zh: >
      A4 纵向 210mm × 297mm；`@page { size: A4; margin: 0 }`，由内容区 padding 12mm 12mm 10mm 控制版心，版心宽 186mm；正文 9.5pt、行高 1.42 时单页约 55 行；超出按行分页并重复表头（thead），页码「第 N 页 / 共 M 页」；页脚三栏（系统与单据名 / 单号 + 模板版本 + 生成时间 / 页码）；屏幕预览按 210mm 1:1；纸张与边距只用 mm/pt。
      
  en: >
      A4 portrait 210mm × 297mm; `@page { size: A4; margin: 0 }` with content padding 12mm 12mm 10mm giving a 186mm measure; at 9.5pt/1.42 roughly 55 lines per page; overflow paginates by row with the table head repeated and page numbers as 第 N 页 / 共 M 页; a three-column footer carries system and document name, number with template version and generation time, and the page number; screen preview renders 1:1 in millimetres; paper and margins use mm/pt only.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.521Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 977
    end_line: 984
  - path: "DESIGN.md"
    line: 1026
    end_line: 1030
  - path: "DESIGN.md"
    line: 1052
    end_line: 1052
apis:
  - protocol: file
    path: "templates/print/base/a4.css"
    description:
      zh: >
          A4 纸张、版心与页脚基础样式。
          
      en: >
          Base styles for A4 paper, measure and footer.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/print/{instance_id}/page-config"
    description:
      zh: >
          读取打印分页与页脚配置。
          
      en: >
          Reads pagination and footer configuration.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/print/{instance_id}/pages"
    description:
      zh: >
          按行分页并返回总页数。
          
      en: >
          Paginates by row and returns the page count.
          
---
