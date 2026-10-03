---
uid: 4a4144e6
id: oa.admin.flow.template.publish
parent: oa.admin.flow.template
state: planned
name: {zh: "版本发布", en: "Versioned Publishing"}
description:
  zh: >
      版本发布：每次变更生成新版本号，已发起实例按发起时的版本与审批人快照执行；模板停用不影响在途实例，回滚也以新版本号实现。
      
  en: >
      Versioned publishing: each change creates a new version, already-started instances keep executing on the version and approver snapshot captured at initiation, and disabling a template leaves in-flight instances untouched.
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.123Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
    line: 368
    end_line: 368
  - path: "doc/data-model.md"
    line: 283
    end_line: 294
apis:
  - protocol: http
    method: POST
    path: "/api/v1/admin/flow-templates/{template_id}/publish"
    description:
      zh: >
          发布新版本（不改历史版本）。
          
      en: >
          Publish a new version without touching history.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/flow-templates/{template_id}/disable"
    description:
      zh: >
          停用模板（不影响在途实例）。
          
      en: >
          Disable a template; in-flight instances are unaffected.
          
  - protocol: http
    method: GET
    path: "/api/v1/admin/flow-templates/{template_id}/versions"
    description:
      zh: >
          查询版本列表与在途实例数。
          
      en: >
          List versions with their in-flight instance counts.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/flow-templates/{template_id}/rollback"
    description:
      zh: >
          回滚到指定版本（生成新版本号）。
          
      en: >
          Roll back by publishing a new version.
          
deps:
  - kind: call
    to: oa.workflow.definition
    from_api: "POST /api/v1/admin/flow-templates/{template_id}/publish"
    label: {zh: "发布模板版本供引擎使用", en: "Publish template version"}
  - kind: event
    to: oa.audit.oplog
    from_api: "POST /api/v1/admin/flow-templates/{template_id}/publish"
    label: {zh: "模板发布写入审计", en: "Publish is audited"}
---
