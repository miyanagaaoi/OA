---
uid: e32e7d34
id: oa.design.a11y.disabled
parent: oa.design.a11y
state: planned
name: {zh: "禁用态与不可读文本", en: "Disabled State Boundary"}
description:
  zh: >
      禁用态边界：ink-disabled 约 2.0:1，只表示「当前不可用」（禁用文字、未到达的流程节点），禁止用在需要阅读的内容上；次要但仍需阅读的文案一律用 ink-subtle；禁用主按钮为 primary-border 底 + ink-muted 文字，仍需可辨「这是按钮、只是不能点」。
      
  en: >
      The disabled boundary: ink-disabled sits at about 2.0:1 and means currently unavailable only (disabled labels, nodes not yet reached), never carrying content that has to be read; text that is secondary yet still needs reading uses ink-subtle; a disabled primary button is a primary-border fill with ink-muted text so it still reads as a button that simply cannot be pressed.
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.691Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 654
    end_line: 654
  - path: "DESIGN.md"
    line: 839
    end_line: 839
  - path: "DESIGN.md"
    line: 931
    end_line: 931
apis:
  - protocol: file
    path: "styles/tokens/disabled.css"
    description:
      zh: >
          不承载需阅读内容的禁用态样式。
          
      en: >
          Disabled-state CSS that never carries content which has to be read.
          
deps:
  - kind: reference
    to: oa.design.token.color.text
    from_api: "file:styles/tokens/disabled.css"
    to_api: "file:styles/tokens/color-ink.css"
    label: {zh: "禁用态文字层级", en: "Disabled ink level"}
---
