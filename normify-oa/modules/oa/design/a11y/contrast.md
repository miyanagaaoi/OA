---
uid: d7279dce
id: oa.design.a11y.contrast
parent: oa.design.a11y
state: planned
name: {zh: "对比度基线", en: "Contrast Baselines"}
description:
  zh: >
      对比度基线以白底计算：正文与背景 ≥4.5:1、大字号 ≥3:1（禁用态豁免）；实测 ink 16.8:1、ink-muted 7.8:1、ink-subtle 5.2:1、warning #9a6200 配白底 4.6:1、inverse-ink 14.6:1、inverse-ink-muted 6.4:1；由 tools/validate-design-md.js 逐组件校验并在低于 AA 时报 error。
      
  en: >
      Contrast baselines are computed on white: body text at 4.5:1 or better and large text at 3:1 or better, with disabled states exempt; measured values are ink 16.8:1, ink-muted 7.8:1, ink-subtle 5.2:1, warning #9a6200 at 4.6:1 on white, inverse-ink 14.6:1 and inverse-ink-muted 6.4:1; tools/validate-design-md.js checks every component and raises an error below AA.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.171Z"
fingerprint: d7d0d1b9e6e41e9590b00d6806c9c43da38139e084006a3b5c0ed7a820865bdb
source:
  - path: "DESIGN.md"
    line: 651
    end_line: 653
  - path: "DESIGN.md"
    line: 671
    end_line: 671
  - path: "doc/prd-0.1.md"
apis:
  - protocol: file
    path: "tools/validate-design-md.js"
    description:
      zh: >
          校验 DESIGN.md 结构、令牌引用与 WCAG 对比度的设计检查器。
          
      en: >
          Design lint that checks structure, token references and WCAG contrast of DESIGN.md.
          
deps:
  - kind: reference
    to: oa.design.token.color.text
    from_api: "file:tools/validate-design-md.js"
    to_api: "file:styles/tokens/color-ink.css"
    label: {zh: "文字层级对比度取值", en: "Ink contrast values"}
---

## 证据锚点
- `doc/prd-0.1.md` → `### 13.3 交付与验收`（§13.3 交付与验收）
