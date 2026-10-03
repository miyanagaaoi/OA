---
uid: acf8bac0
id: oa.design.print.page
parent: oa.design.print
name: {zh: "纸张与版心", en: "Paper & Type Area"}
description:
  zh: >
      A4 纵向 210×297mm；@page margin 0，由内容区 padding 12mm 12mm 10mm 控制版心，版心宽 186mm；正文 9.5pt、行高 1.42 时单页约 55 行；每张单据固定一页，超出时按行分页并重复 thead；屏幕预览按 1:1 毫米尺寸渲染。完整实现见 DESIGN.print-a4.html。
      
  en: >
      A4 portrait at 210×297mm; @page margin 0 with the type area controlled by 12mm/12mm/10mm content padding, giving 186mm of usable width; at 9.5pt body with 1.42 line height a page holds about 55 lines; each document is one page by default, spilling row by row with a repeating thead; the screen preview renders at true millimetre size. Full implementation lives in DESIGN.print-a4.html.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.253Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 975
    end_line: 984
  - path: "DESIGN.md"
    line: 1026
    end_line: 1030
deps:
  - kind: reference
    to: oa.design.token.shape
    label: {zh: "打印稿圆角为 0", en: "Radius 0 in print"}
---
