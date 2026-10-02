---
uid: b4cff79d
id: oa.design.print.rules.units
parent: oa.design.print.rules
state: planned
name: {zh: "单位与样式隔离", en: "Units & Style Isolation"}
description:
  zh: >
      打印样式使用 mm / pt 单位，不用 px 定义纸张与边距；打印稿不复用业务界面的组件类（.btn / .card / .pill 等），避免状态色与圆角渗入；单据内容由数据驱动（字段顺序取表单模板、印章签名取 flow_signature、流水取 sys_thread）；打印时自动隐藏屏幕工具条。
      
  en: >
      Print styles use mm and pt, never px, for paper and margins; print sheets do not reuse the business component classes (.btn, .card, .pill and friends) so status colour and radius cannot leak in; document content is data-driven (field order from the form template, seals and signatures from flow_signature, trail from sys_thread); the on-screen tool bar hides itself when printing.
      
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:07:45.845Z"
fingerprint: pending
source:
  - path: "DESIGN.md"
    line: 1050
    end_line: 1056
  - path: "DESIGN.md"
    line: 1028
    end_line: 1028
apis:
  - protocol: file
    path: "styles/print-a4.css"
    description:
      zh: >
          以 mm / pt 为单位、与界面组件类隔离的打印样式表。
          
      en: >
          Print stylesheet in mm and pt, isolated from the on-screen component classes.
          
deps:
  - kind: reference
    to: oa.design.token.spacing
    from_api: "file:styles/print-a4.css"
    to_api: "file:styles/tokens/spacing.css"
    label: {zh: "打印不使用 px 间距", en: "No px spacing in print"}
---
