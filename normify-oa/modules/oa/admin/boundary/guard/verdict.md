---
uid: cc7892c4
id: oa.admin.boundary.guard.verdict
parent: oa.admin.boundary.guard
state: planned
name: {zh: "已批单据结果冻结", en: "Verdict Freeze"}
description:
  zh: >
      冻结已审批通过单据的审批结果（不可改判）；同时保留在途流程的终止（须填原因）与任务改派能力，两者均留痕。
      
  en: >
      Freezes the verdict of approved documents while still allowing the administrator to terminate an in-flight process with a reason or reassign a task, both of which are audited.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.113Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: PUT
    path: "/api/v1/admin/instances/{instance_id}/verdict"
    description:
      zh: >
          修改已通过单据的审批结果，一律拒绝。
          
      en: >
          Attempt to change an approved verdict; always rejected.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/instances/{instance_id}/terminate"
    description:
      zh: >
          终止在途流程（必须填写原因）。
          
      en: >
          Terminate an in-flight process with a mandatory reason.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/tasks/{task_id}/reassign"
    description:
      zh: >
          在途任务改派（留痕）。
          
      en: >
          Reassign an in-flight task with audit trail.
          
deps:
  - kind: call
    to: oa.workflow.runtime
    from_api: "POST /api/v1/admin/instances/{instance_id}/terminate"
    label: {zh: "终止在途流程", en: "Terminate the in-flight flow"}
  - kind: call
    to: oa.workflow.task
    label: {zh: "在途任务改派", en: "Reassign in-flight task"}
  - kind: dataflow
    to: oa.workflow.runtime.instance.state
    to_api: "mysql:flow_instance"
    label: {zh: "读取单据实例", en: "Read document instance"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-006`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE flow_instance`（§5. 流程运行时）
