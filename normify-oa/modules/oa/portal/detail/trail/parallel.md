---
uid: 337a956e
id: oa.portal.detail.trail.parallel
parent: oa.portal.detail.trail
state: planned
name: {zh: "并行协同分组", en: "Parallel Collaboration Group"}
description:
  zh: >
      并行协同审批分组：多个协同部门折叠为一个分组框，标题显示「协同审批 · N 个部门」并在右侧显示进度（如 3/4），展开可见全部子节点；分组内全部完成才推进，任一协同部门驳回则单据驳回。
      
  en: >
      Parallel collaboration grouping: several co-approving departments fold into one group box whose title reads Collaboration · N departments with progress on the right (for example 3/4) and all child nodes visible when expanded; the flow advances only when the whole group is done, and any rejecting department rejects the document.
      
revision: e3b34a3c59417096ade647fab4261b06f6b605e2
updated_at: "2026-10-03T02:52:03.660Z"
fingerprint: cd9efea98e4b8fd30bd3cc4099150a74c191c187c756c15dfd65a60a05dc8cbc
source:
  - path: "DESIGN.md"
    line: 880
    end_line: 880
  - path: "doc/prd-0.1.md"
    line: 367
    end_line: 367
apis:
  - protocol: http
    method: GET
    path: "/api/v1/portal/detail/{instance_id}/parallel-groups"
    description:
      zh: >
          并行协同分组及其部门完成进度。
          
      en: >
          Parallel collaboration groups with per-department completion progress.
          
deps:
  - kind: call
    to: oa.workflow.runtime
    from_api: "GET /api/v1/portal/detail/{instance_id}/parallel-groups"
    label: {zh: "读取协同任务组", en: "Read parallel task groups"}
  - kind: dataflow
    to: oa.workflow.task
    label: {zh: "协同任务状态", en: "Collaboration task status"}
---
