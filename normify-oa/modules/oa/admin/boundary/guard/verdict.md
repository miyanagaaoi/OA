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
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.617Z"
fingerprint: 45de2030cdb84b6b065d6ae29d070a0561de3211039af1703055f01a49a9f2e4
source:
  - path: "doc/prd-0.1.md"
    line: 440
    end_line: 440
  - path: "doc/prd-0.1.md"
    line: 354
    end_line: 354
  - path: "doc/data-model.md"
    line: 394
    end_line: 395
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
