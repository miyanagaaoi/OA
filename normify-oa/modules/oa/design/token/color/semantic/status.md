---
uid: "82003909"
id: oa.design.token.color.semantic.status
parent: oa.design.token.color.semantic
state: planned
name: {zh: "五套语义色值", en: "Status Color Values"}
description:
  zh: >
      五套语义色与浅底配对：success #1f7a4d / #e6f4ec、warning #9a6200 / #fdf1dd（偏暗琥珀以满足 4.6:1）、error #c02b25 / #fbeaea、info #1f5ae0 / #eef3fe（与 primary 同值——系统里「蓝」只有一个含义）、neutral #5b6472 / #eef0f4。
      
  en: >
      The five semantic colours with their surfaces: success #1f7a4d on #e6f4ec, warning #9a6200 on #fdf1dd (a deliberately darker amber to reach 4.6:1 on white), error #c02b25 on #fbeaea, info #1f5ae0 on #eef3fe (identical to primary because blue means exactly one thing in this system) and neutral #5b6472 on #eef0f4.
      
revision: 44fc7aba1c7e884ffa3553faf31bfce974b388a9
updated_at: "2026-10-03T04:12:20.697Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 670
    end_line: 674
  - path: "DESIGN.md"
    line: 1091
    end_line: 1101
apis:
  - protocol: file
    path: "styles/tokens/color-semantic.css"
    description:
      zh: >
          五套语义色及其浅底配对的 CSS 变量。
          
      en: >
          Semantic colour CSS custom properties, each with its surface pair.
          
deps:
  - kind: reference
    to: oa.design.a11y
    label: {zh: "白底对比度基线", en: "Contrast baseline"}
---
