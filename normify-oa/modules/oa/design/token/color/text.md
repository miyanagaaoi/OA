---
uid: "750e8638"
id: oa.design.token.color.text
parent: oa.design.token.color
state: planned
name: {zh: "文字与禁用态令牌", en: "Text & Disabled Tokens"}
description:
  zh: >
      文字四级：ink #14181f（16.8:1）、ink-muted #4a5563（7.8:1）、ink-subtle #5f6b7a（5.2:1）、ink-disabled #aeb7c4（约 2.0:1）；disabled 只表示「当前不可用」，禁止承载需要阅读的内容，次要但需阅读的文案一律用 ink-subtle。
  en: >
      Four ink levels: ink #14181f at 16.8:1, ink-muted #4a5563 at 7.8:1, ink-subtle #5f6b7a at 5.2:1 and ink-disabled #aeb7c4 at roughly 2.0:1; disabled means currently unavailable only and must never carry content that has to be read, so secondary text that still needs reading always uses ink-subtle.
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:10:00Z"
fingerprint: pending
source:
  - path: "DESIGN.md"
    line: 649
    end_line: 654
  - path: "DESIGN.md"
    line: 1070
    end_line: 1075
apis:
  - protocol: file
    path: "styles/tokens/color-ink.css"
    description:
      zh: >
          文字层级 CSS 变量（含禁用态）。
      en: >
          Ink ladder CSS custom properties including the disabled level.
deps:
  - kind: reference
    to: oa.design.a11y
    label: {zh: "四级对比度基线", en: "Contrast baselines"}
---
