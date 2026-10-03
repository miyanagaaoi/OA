---
uid: a364a8f3
id: oa.design.component.data-display.status-pill
parent: oa.design.component.data-display
name: {zh: "状态徽标", en: "Status Badge"}
description:
  zh: >
      状态徽标 status-pill-*：rounded.sm + 浅底同色深字 + 12px 文字，五态与颜色一一映射且全局唯一（草稿/已关闭=neutral、待我审批=warning、审批中=info、已通过=success、已驳回/终止=error）；不得为不同单据类型发明新配色，也不用饱和填充块。
      
  en: >
      The status-pill badges: rounded.sm with a light same-hue fill and 12px dark text, one-to-one and globally unique mapping between state and colour (draft and closed neutral, pending warning, in approval info, approved success, rejected or terminated error); no document type may invent its own palette and saturated blocks are forbidden.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.420Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 877
    end_line: 877
  - path: "DESIGN.md"
    line: 908
    end_line: 908
  - path: "DESIGN.md"
    line: 1128
    end_line: 1140
apis:
  - protocol: file
    path: "styles/components/status-pill.css"
    description:
      zh: >
          五个全局唯一状态令牌的徽标样式。
          
      en: >
          Status badge CSS for the five globally unique state tokens.
          
deps:
  - kind: reference
    to: oa.design.token.color.semantic.status
    from_api: "file:styles/components/status-pill.css"
    to_api: "file:styles/tokens/color-semantic.css"
    label: {zh: "状态色取值", en: "Status colour values"}
---
