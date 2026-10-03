---
uid: aab953fb
id: oa.form.print.structure.merge-rows
parent: oa.form.print.structure
state: planned
name: {zh: "合并栏与收尾行", en: "Merged Cells & Closing Rows"}
description:
  zh: >
      合同单「集团领导意见」把分管领导与董事长意见放同一单元格（中间留 5–6mm）；收尾行：合同单「公文回传」+「印鉴证照管理部门」、资金单「系统关联」（关联单号 + 审批链）；「其他会审部门」按实单保留为字典驱动的多选字段，可不保留并配置隐藏。
      
  en: >
      The contract sheet merges the executives' and chairman's opinions into one cell with 5–6mm between them; the closing rows are the document return plus seal & certificate administration row for contracts and the system-linkage row (linked number and approval chain) for funds; the other joint-review departments line is kept as a dictionary-driven multi-select that can be hidden if dropped.
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.595Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 1009
    end_line: 1010
  - path: "DESIGN.md"
    line: 1036
    end_line: 1036
apis:
  - protocol: file
    path: "templates/print/partials/merge-rows.html"
    description:
      zh: >
          合并栏与收尾行片段。
          
      en: >
          Partial for merged cells and closing rows.
          
---
