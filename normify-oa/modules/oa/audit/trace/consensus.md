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
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.272Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-LOG-002`（§6.9 审计日志）
- `doc/data-model.md` → `CREATE TABLE sys_thread`（§6. 签名、附件、抄送、消息、审计）
