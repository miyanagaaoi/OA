---
uid: 337a956e
id: oa.portal.detail.trail.parallel
parent: oa.portal.detail.trail
name: {zh: "并行协同分组", en: "Parallel Collaboration Group"}
description:
  zh: >
      并行协同审批分组：多个协同部门折叠为一个分组框，标题显示「协同审批 · N 个部门」并在右侧显示进度（如 3/4），展开可见全部子节点；分组内全部完成才推进，任一协同部门驳回则单据驳回。
      
  en: >
      Parallel collaboration grouping: several co-approving departments fold into one group box whose title reads Collaboration · N departments with progress on the right (for example 3/4) and all child nodes visible when expanded; the flow advances only when the whole group is done, and any rejecting department rejects the document.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.337Z"
fingerprint: 6f97dc580ed4d0d87fae8063d855678f9a2f3b3425b149dd9321be8c4b605b79
source:
  - path: "DESIGN.md"
    line: 880
    end_line: 880
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-LOG-002`（§6.9 审计日志）
