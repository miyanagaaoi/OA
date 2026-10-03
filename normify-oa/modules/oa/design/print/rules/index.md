---
uid: aeb22409
id: oa.design.print.rules
parent: oa.design.print
name: {zh: "打印专用规则", en: "Print-Only Rules"}
description:
  zh: >
      打印稿必须比业务界面更朴素且不可互相套用：圆角全部 0px、投影完全禁止、全表 1pt 实线（外框可加粗至 .6mm）、不使用任何主题色（全部黑色）、状态以文字呈现（已通过 / 已撤回）而非色块、字号固定 9.5pt 正文 / 8.5pt 附注 / 16pt 抬头、数字用等宽字体、黑白复印后信息不得丢失。
      
  en: >
      Print sheets must be plainer than the business UI and the two rule sets may never be mixed: radius is always 0px, shadows are banned outright, the whole table is ruled in 1pt solid lines with an outer frame up to .6mm, no theme colour appears at all, status is written as text (approved, withdrawn) rather than a colour block, type is fixed at 9.5pt body, 8.5pt notes and 16pt heading, numbers use a monospace face and no information may be lost in a black-and-white copy.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.424Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 986
    end_line: 1001
deps:
  - kind: reference
    to: oa.design.token
    label: {zh: "打印稿不套用界面令牌", en: "Tokens not reused in print"}
---
