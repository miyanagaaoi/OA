---
uid: cac2ecde
id: oa.admin.boundary.guard.doc
parent: oa.admin.boundary.guard
state: planned
name: {zh: "单据与日志删除禁令", en: "Deletion Ban Gate"}
description:
  zh: >
      硬闸门：拒绝删除已产生的审批单据与审计日志；拒绝动作本身也写入审计日志，不允许静默失败。
  en: >
      Hard gate that refuses deletion of produced approval documents and audit logs; the refusal itself is written to the audit trail instead of silently failing.
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:10:00Z"
fingerprint: pending
source:
  - path: "doc/prd-0.1.md"
    line: 440
    end_line: 440
  - path: "doc/prd-0.1.md"
    line: 431
    end_line: 431
  - path: "doc/data-model.md"
    line: 748
    end_line: 754
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/boundary/rules"
    description:
      zh: >
          查询管理员边界规则清单。
      en: >
          List the boundary rules enforced on the super-admin.
  - protocol: http
    method: DELETE
    path: "/api/v1/admin/instances/{instance_id}"
    description:
      zh: >
          请求删除单据，一律拒绝并留痕。
      en: >
          Request document deletion; always rejected and logged.
  - protocol: http
    method: DELETE
    path: "/api/v1/admin/audit-logs/{log_id}"
    description:
      zh: >
          请求删除审计日志，一律拒绝并留痕。
      en: >
          Request audit-log deletion; always rejected and logged.
deps:
  - kind: call
    to: oa.audit.integrity
    label: {zh: "只追加约束由审计完整性保障", en: "Append-only enforcement"}
  - kind: reference
    to: oa.workflow.runtime
    label: {zh: "单据删除禁令", en: "Document deletion is banned"}
---
