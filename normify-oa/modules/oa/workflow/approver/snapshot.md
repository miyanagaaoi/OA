---
uid: 2dc34ff2
id: oa.workflow.approver.snapshot
parent: oa.workflow.approver
state: planned
name: {zh: "审批人快照固化", en: "Approver Snapshot Freezing"}
description:
  zh: >
      发起时一次性解析全部节点候选并固化进 flow_instance.approver_snapshot_json（template_version / parsed_at / basis / nodes+evidence）：此后调岗、离职、组织调整、负责人变更均不改动在途单据；驳回重提时重新解析，旧快照留审计（REQ-FLOW-011、REQ-FLOW-017）。
      
  en: >
      Resolves all node candidates once at submission and freezes them into flow_instance.approver_snapshot_json (template_version / parsed_at / basis / nodes with evidence): later transfers, resignations or org changes never alter in-flight instances; resubmission re-resolves the snapshot while the old one stays in the audit log (REQ-FLOW-011/017).
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.422Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/data-model.md"
    line: 670
    end_line: 720
  - path: "doc/prd-0.1.md"
    line: 202
    end_line: 202
  - path: "doc/prd-0.1.md"
    line: 355
    end_line: 355
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow-instances/{instance_id}/approver-snapshot"
    description:
      zh: >
          发起时一次性解析全部节点并固化实例快照。
          
      en: >
          Parses all nodes once and freezes approver_snapshot_json on the instance.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow-instances/{instance_id}/approver-snapshot"
    description:
      zh: >
          读取作为运行时权威数据的快照。
          
      en: >
          Returns the frozen snapshot used as runtime authority.
          
  - protocol: http
    method: POST
    path: "/api/v1/flow-instances/{instance_id}/approver-snapshot/reparse"
    description:
      zh: >
          驳回重提时重新解析，旧快照留审计。
          
      en: >
          Re-resolves the snapshot after rejection while keeping the old one in audit.
          
deps:
  - kind: call
    to: oa.workflow.approver.rule-table
    from_api: "POST /api/v1/flow-instances/{instance_id}/approver-snapshot"
    label: {zh: "逐节点跑解析规则", en: "Run rules per node"}
  - kind: call
    to: oa.workflow.definition.template.version
    from_api: "POST /api/v1/flow-instances/{instance_id}/approver-snapshot"
    to_api: "POST /api/v1/flow-templates/{template_id}/versions"
    label: {zh: "记录锁定版本", en: "Record locked version"}
  - kind: reference
    to: oa.workflow.definition.node-behavior.decision
    from_api: "POST /api/v1/flow-instances/{instance_id}/approver-snapshot"
    to_api: "GET /api/v1/flow-nodes/{node_id}/decision"
    label: {zh: "冻结决议模式", en: "Freeze decision mode"}
  - kind: dataflow
    to: oa.audit.oplog
    from_api: "POST /api/v1/flow-instances/{instance_id}/approver-snapshot/reparse"
    label: {zh: "旧快照留痕", en: "Old snapshot kept in audit"}
---
