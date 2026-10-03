---
uid: ad336cc9
id: oa.design.print.page.height
parent: oa.design.print.page
state: planned
name: {zh: "实测高度与分页预算", en: "Measured Height Budget"}
description:
  zh: >
      四张单据的实测高度（限 297mm）：集团合同类文件流转审批单 274mm、资金审批单 242mm、子公司内部审批单（13 条记录）270mm、印鉴证照使用审批单 205mm；P1 与 P3 余量较小，记录流水超过约 15 条将超出单页，届时允许跨页并重复表头。
      
  en: >
      Measured heights against the 297mm limit: the group contract routing sheet 274mm, the fund approval sheet 242mm, the subsidiary internal sheet with 13 records 270mm and the seal-and-licence sheet 205mm; P1 and P3 have little headroom, so more than roughly 15 trail records overflow one page and must then break across pages with a repeating header.
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.332Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 1039
    end_line: 1048
apis:
  - protocol: file
    path: "DESIGN.print-a4.html"
    description:
      zh: >
          A4 打印稿完整实现（含 4 张单据样张，按实际毫米尺寸）。
          
      en: >
          A4 print implementation with four sample sheets at real millimetre size.
          
deps:
  - kind: reference
    to: oa.form.template
    label: {zh: "记录条数上限", en: "Record count ceiling"}
---
