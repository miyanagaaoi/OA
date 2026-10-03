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
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.648Z"
fingerprint: 7241121ccd161ffddfd3e9166a1a5bc6bd672932d1333daa0959b62334d40745
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
