---
uid: 2f78e243
id: oa.archive.policy.attachment-cold
parent: oa.archive.policy
state: planned
name: {zh: "附件冷存储迁移", en: "Cold Attachment Move"}
description:
  zh: >
      归档时把附件文件随元数据迁至冷存储目录，并保证历史库中的存储路径仍可解析与下载，禁止公网直链。
      
  en: >
      Moves attachment files to cold storage together with their metadata, keeping the stored paths resolvable from the history store; public direct links stay forbidden.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.613Z"
fingerprint: a5c8c53b676315cbf9ccdfb80d068b584cf8e89cdb8f4dda5c25769eecec1871
source:
  - path: "doc/data-model.md"
    line: 825
    end_line: 825
  - path: "doc/data-model.md"
    line: 568
    end_line: 588
apis:
  - protocol: http
    method: POST
    path: "/api/v1/archive/attachments/migrate"
    description:
      zh: >
          把附件文件迁移到冷存储。
          
      en: >
          Migrates attachment files to cold storage.
          
  - protocol: http
    method: GET
    path: "/api/v1/archive/attachments/{attachment_id}/path"
    description:
      zh: >
          解析附件的冷存储路径。
          
      en: >
          Resolves the cold-storage path of an attachment.
          
deps:
  - kind: dataflow
    to: oa.workflow.runtime
    from_api: "POST /api/v1/archive/attachments/migrate"
    label: {zh: "附件元数据随单迁移", en: "Move attachment metadata"}
  - kind: reference
    to: oa.platform.backup
    from_api: "POST /api/v1/archive/attachments/migrate"
    label: {zh: "冷存储目录纳入备份", en: "Back up cold storage dir"}
---
