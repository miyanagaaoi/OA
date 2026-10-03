---
uid: bdbe0b73
id: oa.form.print.style-isolation
parent: oa.form.print
state: planned
name: {zh: "打印样式隔离", en: "Print Style Isolation"}
description:
  zh: >
      打印稿与业务界面共享数据、不共享样式：全部直角 0px、全表 1pt 实线（外框可加粗至 .6mm）、完全禁投影与渐变、不使用主题色（全黑）、状态以文字呈现、字号 9.5pt/8.5pt/16pt 不随屏幕缩放、不复用 .btn/.card/.pill 组件类、纸张与边距只用 mm/pt、黑白复印后信息不得丢失。
      
  en: >
      Sheets share data with the screen UI but never its styles: square corners everywhere, 1pt solid table rules (outer border to .6mm), no shadows or gradients, no theme colour (all black), statuses expressed as text, fixed 9.5pt/8.5pt/16pt sizes, no .btn/.card/.pill reuse, millimetre and point units for paper and margins, and nothing lost in black-and-white copying.
      
revision: 966907fad0c5f0d01bc6a76ddba80bbbf67f586a
updated_at: "2026-10-03T04:41:37.357Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 986
    end_line: 1001
  - path: "DESIGN.md"
    line: 1052
    end_line: 1056
apis:
  - protocol: file
    path: "templates/print/base/print-reset.css"
    description:
      zh: >
          打印样式重置与黑白可读基线。
          
      en: >
          Print style reset and monochrome-readable baseline.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/print/{instance_id}/style-tokens"
    description:
      zh: >
          读取打印稿专用样式令牌。
          
      en: >
          Reads the print-only style tokens.
          
deps:
  - kind: reference
    to: oa.design.print
    from_api: "GET /api/v1/forms/print/{instance_id}/style-tokens"
    label: {zh: "打印设计规范", en: "Print design spec"}
---
