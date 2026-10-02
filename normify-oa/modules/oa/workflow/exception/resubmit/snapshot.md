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
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.813Z"
fingerprint: 6be246c6834b9b56d4a42d5b955150bb6a06a766a90c031fd7f3af6b4c917a6d
source:
  - path: "doc/prd-0.1.md"
    line: 389
    end_line: 389
  - path: "doc/prd-0.1.md"
    line: 202
    end_line: 202
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
