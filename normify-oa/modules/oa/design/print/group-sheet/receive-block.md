---
uid: b7d5be7c
id: oa.design.print.group-sheet.receive-block
parent: oa.design.print.group-sheet
state: planned
name: {zh: "公文接收及处理重复块", en: "Receipt & Handling Block"}
description:
  zh: >
      合同单核心特征：每段为「接收单位 / 接收人 / 签收时间」（内嵌表格分三格，仅竖线）+ 处理意见多行区 + 落款与日期右对齐，最多 3 段；每段对应一次集团层流转（flow_routing），段数随流转实际发生次数渲染。
      
  en: >
      The core feature of the contract sheet: each block holds receiving unit, receiver and signed time as a three-cell embedded table with vertical rules only, a multi-line handling opinion and a right-aligned signature and date, up to three blocks, one per group-level routing hop, rendered according to how many hops actually happened.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.302Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 1007
    end_line: 1007
apis:
  - protocol: file
    path: "print/templates/group-contract-a4.html"
    description:
      zh: >
          集团合同类文件流转审批单模板（含最多 3 段接收块）。
          
      en: >
          Group contract routing sheet template with up to three receipt blocks.
          
deps:
  - kind: dataflow
    to: oa.workflow.routing
    label: {zh: "每段对应一次流转", en: "One block per routing hop"}
---
