---
uid: 0cbe7b25
id: oa.audit.trace.consensus
parent: oa.audit.trace
state: planned
name: {zh: "决议模式结论汇总", en: "Decision Consensus"}
description:
  zh: >
      在或签/会签/依次等决议模式下，按节点汇总各审批人的结论、意见与处理时间，形成「各人结论」视图，作为审批轨迹的组成证据。
      
  en: >
      Aggregates each approver's conclusion, opinion and timestamp per node under or-sign / countersign / sequential decision modes, producing the per-person outcome view that forms part of the approval trail.
      
revision: e3b34a3c59417096ade647fab4261b06f6b605e2
updated_at: "2026-10-03T02:49:57.378Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/prd-0.1.md"
    line: 446
    end_line: 446
  - path: "doc/data-model.md"
    line: 703
    end_line: 718
apis:
  - protocol: http
    method: GET
    path: "/api/v1/instances/{instance_id}/trail/consensus"
    description:
      zh: >
          汇总单据各节点的决议结论。
          
      en: >
          Summarizes node-level decision outcomes of a document.
          
  - protocol: http
    method: GET
    path: "/api/v1/nodes/{node_instance_id}/decisions"
    description:
      zh: >
          列出单个节点下各审批人的结论。
          
      en: >
          Lists each approver's decision in one node.
          
deps:
  - kind: call
    to: oa.workflow.task
    from_api: "GET /api/v1/instances/{instance_id}/trail/consensus"
    label: {zh: "读取节点下各审批任务结论", en: "Read task decisions per node"}
  - kind: reference
    to: oa.workflow.approver
    from_api: "GET /api/v1/nodes/{node_instance_id}/decisions"
    label: {zh: "解析决议模式与通过阈值", en: "Resolve decision mode"}
---
