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
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.523Z"
fingerprint: 3b00610613fe0f45aad673a1508d23c3d3cd2c88a03dfe3d41047751592de232
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
