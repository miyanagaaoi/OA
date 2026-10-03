---
uid: 64de20c8
id: oa.design.token.color.brand
parent: oa.design.token.color
state: planned
name: {zh: "品牌与强调色令牌", en: "Brand & Accent Tokens"}
description:
  zh: >
      品牌与强调色：primary / primary-hover / primary-active / primary-subtle / primary-border / on-primary 六个值，以及集团 logo 规格（透明底 logo-180.png，抽屉头部 28×28、登录页 44×44、打印稿页脚高 8mm）。企业蓝使用白名单严格限定为四项：主按钮背景、文本链接、焦点环、当前选中项。
      
  en: >
      Brand and accent values: primary, primary-hover, primary-active, primary-subtle, primary-border and on-primary, plus the group logo spec (transparent 180×147 artwork used at 28×28 in the drawer header, 44×44 on the sign-in page and 8mm tall in the print footer). The corporate blue is whitelisted to exactly four uses: primary button fill, text links, focus ring and the current selection.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.503Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 616
    end_line: 636
  - path: "DESIGN.md"
    line: 1058
    end_line: 1069
apis:
  - protocol: file
    path: "styles/tokens/color-brand.css"
    description:
      zh: >
          品牌与强调色 CSS 变量（primary / hover / active / subtle / border / on-primary）。
          
      en: >
          Brand and accent CSS custom properties (primary, hover, active, subtle, border, on-primary).
          
  - protocol: file
    path: "logo-180.png"
    description:
      zh: >
          透明底集团 logo，界面上唯一允许的非企业蓝彩色元素。
          
      en: >
          Transparent-background group logo, the only multi-colour element allowed in the UI.
          
deps:
  - kind: reference
    to: oa.design.a11y
    from_api: "file:logo-180.png"
    label: {zh: "蓝底白字对比度", en: "White-on-blue contrast"}
---
