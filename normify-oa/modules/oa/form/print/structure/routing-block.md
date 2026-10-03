---
uid: a8c40497
id: oa.form.print.structure.routing-block
parent: oa.form.print.structure
state: planned
name: {zh: "公文接收及处理块", en: "Document Receipt Block"}
description:
  zh: >
      合同单核心特征，最多 3 段：每段为「接收单位 / 接收人 / 签收时间」（内嵌表格分三格，仅竖线）+ 处理意见多行区 + 落款与日期右对齐；每段对应一次集团层流转。
      
  en: >
      The contract sheet's core feature, up to three segments: each holds receiving unit / receiver / receipt time (a three-cell inline table with vertical rules only), a multi-line handling opinion and a right-aligned sign-off with date; each segment corresponds to one group-level routing.
      
revision: c22d447e6e63ccb0edfd9624026f21e8d1413077
updated_at: "2026-10-03T02:20:07.642Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 1007
    end_line: 1007
  - path: "DESIGN.md"
    line: 1054
    end_line: 1054
apis:
  - protocol: file
    path: "templates/print/partials/routing-block.html"
    description:
      zh: >
          公文接收及处理重复块片段。
          
      en: >
          Partial for the repeated document-receipt block.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/print/{instance_id}/routing-blocks"
    description:
      zh: >
          取流转记录生成接收处理段。
          
      en: >
          Builds receipt segments from routing records.
          
deps:
  - kind: dataflow
    to: oa.workflow.routing
    from_api: "GET /api/v1/forms/print/{instance_id}/routing-blocks"
    label: {zh: "集团层流转记录", en: "Group routing records"}
---
