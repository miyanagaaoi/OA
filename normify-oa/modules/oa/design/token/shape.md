---
uid: 86a89b26
id: oa.design.token.shape
parent: oa.design.token
state: planned
name: {zh: "圆角与形状令牌", en: "Radius & Shape Tokens"}
description:
  zh: >
      圆角尺度：none 0px（面板、表格、侧栏）、xs 2px（徽标、标签、复选框）、sm 4px（按钮、输入框、下拉框）、md 6px（卡片、模态框、上传区）、lg 8px（大面板、流程设计器画布）、pill / full 9999px（仅开关轨道、胶囊标签与头像）；按钮圆角恒为 4px，永不使用 pill 圆角按钮。
      
  en: >
      The radius scale: none 0px for panels, tables and the sidebar, xs 2px for badges and checkboxes, sm 4px for buttons and inputs, md 6px for cards, modals and the upload area, lg 8px for large panels and the designer canvas, pill and full 9999px reserved for switch tracks and avatars; buttons are always 4px and never pill-shaped, because capsule buttons weaken the formality of an approval system.
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.202Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 816
    end_line: 828
  - path: "DESIGN.md"
    line: 1113
    end_line: 1118
apis:
  - protocol: file
    path: "styles/tokens/shape.css"
    description:
      zh: >
          从 none 到 full 的圆角 CSS 变量。
          
      en: >
          Border-radius CSS custom properties from none to full.
          
---
