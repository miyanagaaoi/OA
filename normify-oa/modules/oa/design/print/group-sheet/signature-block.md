---
uid: bbd9880d
id: oa.design.print.group-sheet.signature-block
parent: oa.design.print.group-sheet
state: planned
name: {zh: "多轮签名栏", en: "Multi-Round Signature Bands"}
description:
  zh: >
      资金单的三段签名栏：「集团职能部门 / 集团分管领导 / 集团董事长」，每段含「签名：____ 年 月 日」。**正式 A4 打印稿签名栏一律为空栏**（供手签）；仅屏幕预览显示已签署的缩略图与时间戳（V0.4）。归口统一为财务部，但打印稿保留实单标签「集团职能部门」。
  en: >
      The fund sheet's three-part signature block (group function department / group line leader / chairman), each with a blank signature line and date. Formal A4 printouts always leave the signature area blank for handwriting; only the on-screen preview shows the signed thumbnail with its timestamp. The print sheet keeps the paper form's label "group function department" even though Finance is the single owner.
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:31:36.607Z"
fingerprint: pending
source:
  - path: "DESIGN.md"
    line: 1008
    end_line: 1008
  - path: "DESIGN.md"
    line: 1037
    end_line: 1037
  - path: "doc/forms.md"
    line: 369
    end_line: 369
apis:
  - protocol: file
    path: "print/templates/group-fund-a4.html"
    description:
      zh: >
          资金审批单模板（含三段签名栏）。
          
      en: >
          Fund approval sheet template with three signature bands.
          
deps:
  - kind: dataflow
    to: oa.sign.record
    label: {zh: "签名按欄位打印", en: "Signatures printed in place"}
---
