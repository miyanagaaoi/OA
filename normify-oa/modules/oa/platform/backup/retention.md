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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.762Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
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
