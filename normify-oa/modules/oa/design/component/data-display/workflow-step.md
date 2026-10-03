---
uid: a64e0863
id: oa.design.component.data-display.workflow-step
parent: oa.design.component.data-display
state: planned
name: {zh: "流程节点四态", en: "Workflow Step"}
description:
  zh: >
      审批轨迹节点四态与并行分组：已完成（白底 + primary-border 边 + 墨色文字 + 勾选图标）、进行中（企业蓝实心 + 白字）、未到达（白底灰字灰边）、超时（白底红边红字）；节点间连接线 2px，已完成段 primary、未完成段 hairline；并行分组显示「协同审批 · N 个部门」与进度 3/4。
      
  en: >
      The four flow node states plus parallel grouping: done (white with a primary-border outline, ink text and a tick), current (solid corporate blue with white text), not reached (white with grey text and border) and overdue (white with red text and border); connectors are 2px with completed segments in primary and unfinished in hairline, and a parallel group shows Collaboration with the department count and 3/4 progress.
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.257Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 879
    end_line: 880
  - path: "DESIGN.md"
    line: 922
    end_line: 922
apis:
  - protocol: file
    path: "styles/components/workflow-step.css"
    description:
      zh: >
          流程节点样式：四态圆点、2px 连接线与并行分组容器。
          
      en: >
          Flow step CSS: four node states, 2px connectors and parallel group container.
          
deps:
  - kind: reference
    to: oa.design.token.color.semantic.status
    from_api: "file:styles/components/workflow-step.css"
    to_api: "file:styles/tokens/color-semantic.css"
    label: {zh: "节点四态取色", en: "Node state colours"}
---
