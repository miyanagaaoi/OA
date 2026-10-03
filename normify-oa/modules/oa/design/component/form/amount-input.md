---
uid: 9e3f1b44
id: oa.design.component.form.amount-input
parent: oa.design.component.form
state: planned
name: {zh: "金额输入控件", en: "Amount Input"}
description:
  zh: >
      金额控件：typography.amount + tnum、右对齐、输入时显示千分位、左侧固定「¥」前缀、失焦规范为两位小数；金额为 0 或空时禁止提交（资金单金额必填）；≥100 万在展示层同时给出万元换算以降低误读风险。
      
  en: >
      The amount control: typography.amount with tnum, right-aligned, thousands separators while typing, a fixed yuan prefix on the left and normalisation to two decimals on blur; an amount of zero or blank blocks submission, and above one million the display also shows a ten-thousand-yuan conversion to cut misreading risk.
      
revision: 44fc7aba1c7e884ffa3553faf31bfce974b388a9
updated_at: "2026-10-03T04:12:20.690Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 849
    end_line: 849
  - path: "DESIGN.md"
    line: 876
    end_line: 876
  - path: "DESIGN.md"
    line: 909
    end_line: 909
apis:
  - protocol: file
    path: "styles/components/amount-input.css"
    description:
      zh: >
          金额输入样式：等宽数字、右对齐、千分位与「¥」前缀。
          
      en: >
          Amount input CSS: tabular numerals, right alignment, thousands separators and the yuan prefix.
          
deps:
  - kind: reference
    to: oa.design.token.typography
    from_api: "file:styles/components/amount-input.css"
    to_api: "file:styles/tokens/typography.css"
    label: {zh: "金额与等宽字阶", en: "Amount & mono type scale"}
---
