---
uid: 94880ef4
id: oa.design.component.button
parent: oa.design.component
state: planned
name: {zh: "按钮", en: "Buttons"}
description:
  zh: >
      按钮五族：button-primary（每屏仅一个，主色底白字、环4px 圆角、高 32px、水平内边距 16px，悬停压暗、按下更深、禁用为 primary-border 底 + ink-muted 文字）、button-secondary（白底 + hairline-strong 边）、button-ghost（行内操作，primary 文字）、button-danger（破坏性，驳回优先用白底红字变体）、button-h5-primary / secondary（H5 底部 44px）。
      
  en: >
      The five button families: primary (exactly one per screen, corporate-blue fill with white text, 4px radius, 32px tall and 16px horizontal padding, darkening on hover and press, disabled as a primary-border fill with ink-muted text), secondary (white with a hairline-strong border), ghost (row actions in primary text), danger (destructive, with a red-on-white variant preferred for reject) and the 44px H5 primary and secondary pair.
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.254Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 837
    end_line: 843
  - path: "DESIGN.md"
    line: 906
    end_line: 906
apis:
  - protocol: file
    path: "styles/components/button.css"
    description:
      zh: >
          按钮族样式：主按钮、次按钮、行内文字按钮、危险按钮与 H5 44px 双按钮。
          
      en: >
          Button family CSS: primary, secondary, ghost, danger and the 44px H5 pair.
          
  - protocol: http
    method: GET
    path: "/design/components/button"
    description:
      zh: >
          列举全部按钮变体及悬停 / 按下 / 禁用态的规范页。
          
      en: >
          Spec page listing every button variant with hover, press and disabled states.
          
deps:
  - kind: reference
    to: oa.design.token.color.brand
    from_api: "file:styles/components/button.css"
    to_api: "file:styles/tokens/color-brand.css"
    label: {zh: "主色与危险色底", en: "Primary & danger fills"}
  - kind: reference
    to: oa.design.token.shape
    from_api: "file:styles/components/button.css"
    to_api: "file:styles/tokens/shape.css"
    label: {zh: "控件圆角令牌", en: "Control radius tokens"}
---
