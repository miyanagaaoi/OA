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
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.677Z"
fingerprint: 45de2030cdb84b6b065d6ae29d070a0561de3211039af1703055f01a49a9f2e4
source:
  - path: "doc/prd-0.1.md"
    line: 427
    end_line: 427
  - path: "doc/data-model.md"
    line: 459
    end_line: 460
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
