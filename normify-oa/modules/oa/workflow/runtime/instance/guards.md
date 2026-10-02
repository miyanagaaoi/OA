---
uid: 3995642b
id: oa.workflow.runtime.instance.guards
parent: oa.workflow.runtime.instance
state: planned
name: {zh: "迁移守卫与终态规则", en: "Transition Guards & Terminal Rules"}
description:
  zh: >
      迁移前置守卫：终态（approved/rejected/withdrawn/terminated）不可再提交；撤回仅限发起人且在节点②通过前；驳回后重提须重新解析快照与最新模板版本、已审节点不保留；终止仅系统管理员与集团分管领导且必填原因（REQ-FLOW-009/010/014/017、AC-15）。
      
  en: >
      Pre-transition guards: terminal states (approved/rejected/withdrawn/terminated) can never be resubmitted; withdrawal belongs to the initiator only and only before node ② passes; resubmission after rejection re-resolves the snapshot against the latest template version and drops already-approved nodes; termination is limited to the system admin and group line leader with a mandatory reason (REQ-FLOW-009/010/014/017, AC-15).
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.822Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 353
    end_line: 354
  - path: "doc/prd-0.1.md"
    line: 389
    end_line: 389
  - path: "doc/prd-0.1.md"
    line: 481
    end_line: 483
  - path: "doc/prd-0.1.md"
    line: 506
    end_line: 507
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow-instances/{instance_id}/transition-guard"
    description:
      zh: >
          校验动作是否允许并返回拒绝原因。
          
      en: >
          Validates whether the requested action is allowed and returns the reason.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow-instances/{instance_id}/transition-guard/matrix"
    description:
      zh: >
          返回各状态允许的迁移矩阵供前端禁用按钮。
          
      en: >
          Returns the allowed transitions per status for the UI.
          
  - protocol: kafka
    path: "oa.workflow.instance.guard-rejected"
    description:
      zh: >
          迁移被拒事件，写入审计。
          
      en: >
          Rejected transition event recorded for audit.
          
deps:
  - kind: call
    to: oa.workflow.runtime.counters
    from_api: "POST /api/v1/flow-instances/{instance_id}/transition-guard"
    to_api: "POST /api/v1/flow-instances/{instance_id}/counters/check"
    label: {zh: "查计数闸门", en: "Check counter gates"}
  - kind: call
    to: oa.workflow.approver.snapshot
    to_api: "POST /api/v1/flow-instances/{instance_id}/approver-snapshot/reparse"
    label: {zh: "重提重解析快照", en: "Re-resolve snapshot"}
  - kind: dataflow
    to: oa.audit.oplog
    from_api: "kafka:oa.workflow.instance.guard-rejected"
    label: {zh: "拒绝与原因留痕", en: "Refusal & reason to log"}
---
