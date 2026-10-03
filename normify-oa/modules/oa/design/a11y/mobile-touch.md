---
uid: e651ea7d
id: oa.design.a11y.mobile-touch
parent: oa.design.a11y
name: {zh: "H5 触控与安全区", en: "H5 Touch & Safe Area"}
description:
  zh: >
      H5 可点击元素 ≥44px（控件 44px、底部操作栏 60px 常驻并适配 env(safe-area-inset-bottom)）；水印不遮挡操作区；列表用卡片列表；桌面端的 32px 控件与 44px 行高不得直接搬到 H5，spacing.control-h5 是下限。
      
  en: >
      H5 clickable elements are at least 44px (44px controls and a persistent 60px bottom bar padded for the safe-area inset); the watermark never covers the action area; lists become card lists; the desktop 32px controls and 44px rows must not be carried over unchanged, because the H5 control token is the floor.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.419Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 724
    end_line: 724
  - path: "DESIGN.md"
    line: 791
    end_line: 791
  - path: "DESIGN.md"
    line: 923
    end_line: 923
apis:
  - protocol: file
    path: "styles/tokens/touch-target.css"
    description:
      zh: >
          强制 H5 44px 下限与安全区内边距的触控尺寸样式。
          
      en: >
          Touch target CSS enforcing the 44px H5 minimum and safe-area padding.
          
deps:
  - kind: reference
    to: oa.portal.h5
    label: {zh: "由 H5 骨架消费", en: "H5 shell consumes it"}
---
