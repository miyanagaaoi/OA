---
uid: bee0db52
id: oa.platform.backup.retention
parent: oa.platform.backup
name: {zh: "备份保留与清理", en: "Backup Retention"}
description:
  zh: >
      备份集的保留与清理规则：每日全量保留 30 天、到期按计划清理；与审计相关的导出按 10 年保留要求处理。
      
  en: >
      Retention and cleanup rules for backup sets: thirty days of daily fulls, older sets pruned on schedule, and audit-relevant exports kept according to the ten-year retention rule.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.095Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/backup/jobs"
    description:
      zh: >
          查询备份任务历史与保留状态。
          
      en: >
          Reads backup job history and retention status.
          
  - protocol: file
    path: "backup/retention-policy.yml"
    description:
      zh: >
          备份保留与清理策略。
          
      en: >
          Backup retention and cleanup policy.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-NFR-008`（§第9章 非功能需求）
