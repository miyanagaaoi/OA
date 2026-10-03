---
uid: 4a4144e6
id: oa.admin.flow.template.publish
parent: oa.admin.flow.template
name: {zh: "版本发布", en: "Versioned Publishing"}
description:
  zh: >
      版本发布：每次变更生成新版本号，已发起实例按发起时的版本与审批人快照执行；模板停用不影响在途实例，回滚也以新版本号实现。
      
  en: >
      Versioned publishing: each change creates a new version, already-started instances keep executing on the version and approver snapshot captured at initiation, and disabling a template leaves in-flight instances untouched.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:39.887Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-002`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE flow_template`（§4. 流程定义）
