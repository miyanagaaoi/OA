---
uid: c4b4d169
id: oa.admin.boundary.fallback
parent: oa.admin.boundary
state: planned
name: {zh: "兜底权限与改派", en: "Super-Admin Fallback"}
description:
  zh: >
      定义系统管理员的兜底能力集，预校验某操作是否落入禁区，并提供快照审批人无法处理时的兜底改派能力（必须留痕）。
      
  en: >
      Defines the super-admin fallback capability set, pre-checks whether a requested operation is forbidden, and provides the audited fallback reassignment used when snapshot approvers cannot act.
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.618Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 440
    end_line: 440
  - path: "doc/prd-0.1.md"
    line: 202
    end_line: 202
  - path: "doc/prd-0.1.md"
    line: 238
    end_line: 238
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/super-admin/capabilities"
    description:
      zh: >
          查询兜底能力清单（可配置一切但不可删除/不可改判）。
          
      en: >
          List what the super-admin may and may not do.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/super-admin/fallback-check"
    description:
      zh: >
          校验某操作是否落入禁区。
          
      en: >
          Check whether an operation falls into a forbidden zone.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/tasks/{task_id}/fallback-reassign"
    description:
      zh: >
          快照审批人均无法处理时的兜底改派。
          
      en: >
          Fallback reassign when no snapshot approver can act.
          
deps:
  - kind: call
    to: oa.workflow.task
    from_api: "POST /api/v1/admin/tasks/{task_id}/fallback-reassign"
    label: {zh: "兜底改派审批任务", en: "Fallback reassign task"}
  - kind: call
    to: oa.admin.boundary.guard
    label: {zh: "禁区校验由边界闸门执行", en: "Boundary rules are enforced"}
---
