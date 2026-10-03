---
uid: bee0db52
id: oa.platform.backup.retention
parent: oa.platform.backup
state: planned
name: {zh: "备份保留与清理", en: "Backup Retention"}
description:
  zh: >
      备份集的保留与清理规则：每日全量保留 30 天、到期按计划清理；与审计相关的导出按 10 年保留要求处理。
      
  en: >
      Retention and cleanup rules for backup sets: thirty days of daily fulls, older sets pruned on schedule, and audit-relevant exports kept according to the ten-year retention rule.
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.757Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 538
    end_line: 538
  - path: "doc/prd-0.1.md"
    line: 537
    end_line: 537
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
