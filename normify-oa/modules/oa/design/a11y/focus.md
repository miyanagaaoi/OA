---
uid: da32607c
id: oa.design.a11y.focus
parent: oa.design.a11y
state: planned
name: {zh: "键盘焦点环", en: "Keyboard Focus Ring"}
description:
  zh: >
      焦点环全系统不可移除：0 0 0 2px canvas, 0 0 0 4px focus-ring，仅键盘可见（:focus-visible，鼠标点击不显示）；审批是键盘密集型操作且审计要求可操作性可追溯，因此任何组件都不得 outline: none。
      
  en: >
      The focus ring can never be removed: a 2px canvas ring followed by a 4px focus-ring ring, shown only for keyboard use via :focus-visible and not on mouse click; approval work is keyboard-heavy and auditability requires traceable operability, so no component may set outline none.
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.692Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 679
    end_line: 679
  - path: "DESIGN.md"
    line: 803
    end_line: 803
  - path: "DESIGN.md"
    line: 924
    end_line: 924
apis:
  - protocol: file
    path: "styles/components/focus-ring.css"
    description:
      zh: >
          全系统不可移除的焦点环样式。
          
      en: >
          Focus ring style that must never be removed anywhere in the system.
          
deps:
  - kind: reference
    to: oa.design.token.color.brand
    from_api: "file:styles/components/focus-ring.css"
    to_api: "file:styles/tokens/color-brand.css"
    label: {zh: "焦点环取色", en: "Focus ring colour"}
---
