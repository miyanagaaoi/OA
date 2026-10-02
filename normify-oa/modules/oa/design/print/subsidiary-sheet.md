---
uid: c6396824
id: oa.design.print.subsidiary-sheet
parent: oa.design.print
state: planned
name: {zh: "子公司内部审批单结构", en: "Subsidiary Internal Sheet"}
description:
  zh: >
      子公司单结构与集团单不同：顶部信息条（左「申请编号」、右「打印人 / 打印时间」）、居中加粗抬头、竖向四列字段表（标签|值|标签|值，一行一字段）、整行合并居中的分组标题行（审批详情 / 合同有效期 / 我方信息 / 对方信息 / 审批记录）、审批记录流水每行三段（阶段 · 处理人/动作/时间 · 意见与附件独占整行宽）、撤回与重审各占独立一行。
      
  en: >
      The subsidiary sheet differs from the group sheet: an info strip (application number left, printer and print time right), a centred bold heading, a four-column vertical field table (label, value, label, value; one field per row), full-width centred group titles (approval detail, contract term, our side, counterparty, approval records), a trail of three-part rows (stage, handler/action/time, full-width opinion and attachments) and withdrawn and re-approved each on their own row.
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.688Z"
fingerprint: ac0ee794ab636d5631ecf55f3fe0063493d1e3cafae26bb19c1ca5c017904aea
source:
  - path: "DESIGN.md"
    line: 1015
    end_line: 1024
  - path: "DESIGN.md"
    line: 972
    end_line: 972
apis:
  - protocol: file
    path: "print/templates/subsidiary-internal-a4.html"
    description:
      zh: >
          子公司内部审批单模板（四列字段表与审批记录流水）。
          
      en: >
          Subsidiary internal approval sheet template with the four-column field table and trail rows.
          
deps:
  - kind: dataflow
    to: oa.audit.trace
    label: {zh: "流水取自审批线程", en: "Trail rows from threads"}
---
