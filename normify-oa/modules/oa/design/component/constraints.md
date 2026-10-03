---
uid: acb7dbb7
id: oa.design.component.constraints
parent: oa.design.component
state: planned
name: {zh: "组件使用硬约束", en: "Component Hard Constraints"}
description:
  zh: >
      六条硬约束与 Do / Don't 清单：一屏一主按钮；列表先问能不能用表格；状态只从 status-pill 五令牌里选；金额永远 tnum + 右对齐 + 两位小数；危险操作二次确认且文案含动作与对象；权限不可见优于不可用。另附禁止清单（蓝色做背景、第二彩色、pill 按钮、彩色渐变、Toast 承载流程结果）。
      
  en: >
      The six hard rules plus the Do and Don't list: one primary button per screen; ask whether a list can be a table first; pick status only from the five status-pill tokens; amounts are always tnum, right-aligned with two decimals; destructive actions need a second confirmation naming action and object; invisible beats disabled for permissions — plus the forbidden list (blue backgrounds, a second accent colour, pill buttons, gradients, Toast as a flow-result channel).
      
revision: 112ab0a1d46779714029044fc8e0b46627804f30
updated_at: "2026-10-03T01:51:21.564Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 904
    end_line: 938
  - path: "DESIGN.md"
    line: 939
    end_line: 949
apis:
  - protocol: file
    path: "DESIGN.preview.html"
    description:
      zh: >
          全页面预览（含各页面与反例对照）。
          
      en: >
          Full-page preview showing every screen plus deliberate counter-examples.
          
deps:
  - kind: reference
    to: oa.design.token
    label: {zh: "仅用令牌写样式", en: "Token-only styling"}
  - kind: reference
    to: oa.design.a11y
    label: {zh: "对比度验收", en: "Contrast acceptance"}
---
