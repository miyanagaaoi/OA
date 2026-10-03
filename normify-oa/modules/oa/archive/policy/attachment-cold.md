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
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.440Z"
fingerprint: 4e545cc1c566ce9e10c8fb0b82fc64bfd49ae277034ea19e5092531bb0c1231e
source:
  - path: "doc/data-model.md"
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

## 证据锚点
- `doc/data-model.md` → `## 10. 归档策略（对应 REQ-NFR-010）`（§10. 归档策略）
