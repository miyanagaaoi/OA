---
uid: e6166d07
id: oa.design.a11y.motion
parent: oa.design.a11y
state: planned
name: {zh: "动效与减弱动效", en: "Motion & Reduced Motion"}
description:
  zh: >
      动效克制：抽屉宽度过渡 160ms ease-out，浮层与 Toast 只做淡入淡出；prefers-reduced-motion 下取消动画；不使用渐变、光斑、玻璃拟态与彩色投影——审批界面不做视觉特效。
      
  en: >
      Motion stays restrained: the drawer width transitions over 160ms ease-out and overlays and toasts only fade; animations are cancelled under prefers-reduced-motion; gradients, glow, glassmorphism and coloured shadows are all banned because an approval interface does not do visual effects.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.497Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 776
    end_line: 776
  - path: "DESIGN.md"
    line: 811
    end_line: 811
  - path: "DESIGN.md"
    line: 932
    end_line: 932
apis:
  - protocol: file
    path: "styles/tokens/motion.css"
    description:
      zh: >
          包含 160ms ease-out 抽屉过渡与减弱动效覆盖的动效样式。
          
      en: >
          Motion CSS with the 160ms ease-out drawer transition and reduced-motion override.
          
deps:
  - kind: reference
    to: oa.design.token.panel
    from_api: "file:styles/tokens/motion.css"
    to_api: "file:styles/tokens/panel.css"
    label: {zh: "抽屉过渡令牌", en: "Drawer transition token"}
---
