---
uid: "85102981"
id: oa.design.token.typography
parent: oa.design.token
state: planned
name: {zh: "字体与层级令牌", en: "Typography Tokens"}
description:
  zh: >
      字体族（Inter + PingFang SC / Microsoft YaHei / Noto Sans SC 回退；等宽 JetBrains Mono + Consolas）与 11 级字阶（display 28/600 → button 14/500）；正文 14px 是默认值不是最小值；数字一律等宽 tnum；层级靠字重不靠字号跳跃；中文不使用负字距。
      
  en: >
      Font families (Inter with PingFang SC / Microsoft YaHei / Noto Sans SC fallbacks, and JetBrains Mono with Consolas for identifiers) and eleven type levels from display 28/600 to button 14/500; 14px body is the default rather than a minimum, numerals are always tabular, hierarchy comes from weight rather than size jumps and Chinese never takes negative letter-spacing.
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.693Z"
fingerprint: ac0ee794ab636d5631ecf55f3fe0063493d1e3cafae26bb19c1ca5c017904aea
source:
  - path: "DESIGN.md"
    line: 681
    end_line: 717
  - path: "DESIGN.md"
    line: 690
    end_line: 704
apis:
  - protocol: file
    path: "styles/tokens/typography.css"
    description:
      zh: >
          字体族与 11 级字阶的 CSS 变量。
          
      en: >
          Typography CSS custom properties for families and the eleven-step type scale.
          
---
