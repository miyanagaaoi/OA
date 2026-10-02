---
uid: b253ad6a
id: oa.design.print.rules.mono-copy
parent: oa.design.print.rules
state: planned
name: {zh: "黑白复印可读性", en: "Monochrome Copy Safety"}
description:
  zh: >
      黑白复印后信息不得丢失：任何仅靠颜色区分的信息都必须补文字；状态、风险与勾选一律以文字符号（☑ / ☐）或文字表达；写值区给 3–4% 极浅灰底（#fafafa）在屏幕可辨、打印几乎不可见，标签列不填色。
      
  en: >
      No information may be lost in a black-and-white copy: anything distinguished by colour alone must also carry text, status and risk and selection are expressed as words or as the check-box glyphs, and fillable cells get a 3–4% grey wash (#fafafa) visible on screen but all but invisible in print while label columns stay unfilled.
      
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:07:45.845Z"
fingerprint: pending
source:
  - path: "DESIGN.md"
    line: 1001
    end_line: 1001
  - path: "DESIGN.md"
    line: 1011
    end_line: 1012
  - path: "DESIGN.md"
    line: 1055
    end_line: 1055
apis:
  - protocol: file
    path: "print/mono-copy-checklist.md"
    description:
      zh: >
          黑白复印信息不丢失的自检清单。
          
      en: >
          Checklist proving no information depends on colour after a monochrome copy.
          
deps:
  - kind: reference
    to: oa.design.token.color.semantic.status
    from_api: "file:print/mono-copy-checklist.md"
    to_api: "file:styles/tokens/color-semantic.css"
    label: {zh: "打印稿状态以文字呈现", en: "Status as text in print"}
---
