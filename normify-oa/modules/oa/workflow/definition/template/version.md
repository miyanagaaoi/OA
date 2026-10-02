---
uid: 0479b094
id: oa.workflow.definition.template.version
parent: oa.workflow.definition.template
state: planned
name: {zh: "模板版本与发布状态", en: "Template Versioning & Publish State"}
description:
  zh: >
      模板版本累积与状态机（draft 草稿 / published 已发布 / archived 已归档）：基于已发布版本开新草稿、发布时递增 version 并记 published_at，历史版本只累积不覆盖；已发起实例锁定发起时版本，停用不影响在途（REQ-FLOW-006、AC-09）。
      
  en: >
      Version accumulation and lifecycle (draft/published/archived): open a new draft from a published version, bump version and record published_at on publish, never overwrite history; in-flight instances stay on the version captured at submission (REQ-FLOW-006, AC-09).
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.801Z"
fingerprint: 1ebf4aee4ac8648bfaec0ea0afbfecc1b3e2f5fecc0dff532be914d9e85be0f2
source:
  - path: "doc/prd-0.1.md"
    line: 349
    end_line: 349
  - path: "doc/data-model.md"
    line: 282
    end_line: 295
  - path: "doc/prd-0.1.md"
    line: 579
    end_line: 579
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow-templates/{template_id}/versions"
    description:
      zh: >
          基于已发布版本开新草稿版本（version+1）。
          
      en: >
          Opens a new draft version (version+1) from a published version.
          
  - protocol: http
    method: POST
    path: "/api/v1/flow-templates/{template_id}/publish"
    description:
      zh: >
          发布模板版本并写入 published_at。
          
      en: >
          Publishes the template version and records published_at.
          
  - protocol: http
    method: POST
    path: "/api/v1/flow-templates/{template_id}/archive"
    description:
      zh: >
          归档模板版本（不影响在途实例）。
          
      en: >
          Archives a template version without affecting in-flight instances.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow-templates/{template_id}/versions"
    description:
      zh: >
          列出历史版本与各自状态。
          
      en: >
          Lists historical versions with their statuses.
          
deps:
  - kind: call
    to: oa.workflow.definition.template.registry
    from_api: "POST /api/v1/flow-templates/{template_id}/versions"
    to_api: "POST /api/v1/flow-templates"
    label: {zh: "复制元数据开新版本", en: "Clone metadata to new version"}
  - kind: call
    to: oa.admin.flow
    from_api: "POST /api/v1/flow-templates/{template_id}/publish"
    label: {zh: "发布落管理后台", en: "Admin console publishes"}
---
