---
uid: cc1b951f
id: oa.design.print.header-footer
parent: oa.design.print
state: planned
name: {zh: "页眉页脚与呈现", en: "Header, Footer & Presentation"}
description:
  zh: >
      页脚固定三栏：左「系统名 · 单据名」、中「单号 · 模板版本 · 生成时间」、右「第 N 页 / 共 M 页」；打印时自动隐藏屏幕工具条（.bar）；屏幕预览按 1:1 毫米尺寸渲染（width: 210mm）与打印结果一致；页脚 logo 高 8mm。
      
  en: >
      A fixed three-column footer: system and document name on the left, number, template version and generation time in the middle and page N of M on the right; the on-screen tool bar hides itself when printing; the screen preview renders at true millimetre size (width 210mm) to match the printed result; the footer logo is 8mm tall.
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.704Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 1026
    end_line: 1030
  - path: "DESIGN.md"
    line: 624
    end_line: 624
apis:
  - protocol: file
    path: "print/footer-bar.html"
    description:
      zh: >
          页脚三栏片段（系统与单据 / 单号与版本 / 页码）。
          
      en: >
          Three-column footer fragment: system and document, number and version, page count.
          
  - protocol: file
    path: "print/preview-frame.css"
    description:
      zh: >
          按 210mm 实际宽度渲染的屏幕预览样式。
          
      en: >
          Screen preview stylesheet rendering the sheet at true 210mm width.
          
deps:
  - kind: reference
    to: oa.design.print.page
    label: {zh: "与版心对齐", en: "Page box alignment"}
---
