---
uid: "7267e291"
id: oa.workflow.exception.resubmit.snapshot
parent: oa.workflow.exception.resubmit
state: planned
name: {zh: "重解析审批人快照", en: "Re-resolve Approver Snapshot"}
description:
  zh: >
      重新提交时重新解析审批人快照与流程版本：按发起时点的最新模板版本与新组织关系解析候选人，覆盖旧的 approver_snapshot_json 与 template_version；不再沿用上次提交的快照。
      
  en: >
      On resubmission the approver snapshot and process version are resolved again: candidates come from the latest template version and the current organisation, overwriting the previous snapshot and frozen version rather than reusing them.
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.304Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
    line: 408
    end_line: 408
  - path: "doc/data-model.md"
    line: 393
    end_line: 393
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/instances/{instance_id}/resubmit"
    description:
      zh: >
          重新提交（重解析快照与最新模板版本）。
          
      en: >
          Resubmit: re-resolve the snapshot and use the latest template version.
          
  - protocol: http
    method: POST
    path: "/api/v1/flow/instances/{instance_id}/snapshot/reparse"
    description:
      zh: >
          重新解析审批人快照并固化。
          
      en: >
          Re-resolve the approver snapshot and freeze it again.
          
deps:
  - kind: call
    to: oa.workflow.approver
    from_api: "POST /api/v1/flow/instances/{instance_id}/snapshot/reparse"
    label: {zh: "按解析规则重算候选人", en: "Re-resolve candidate rules"}
  - kind: call
    to: oa.workflow.definition
    from_api: "POST /api/v1/flow/instances/{instance_id}/resubmit"
    label: {zh: "取最新模板版本", en: "Pick latest template version"}
---
