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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.712Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
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
