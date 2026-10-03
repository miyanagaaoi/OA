---
uid: b5f3e9dd
id: oa.platform.backup.daily
parent: oa.platform.backup
state: planned
name: {zh: "每日全量备份", en: "Daily Full Backup"}
description:
  zh: >
      数据库每日全量备份、保留 30 天，任务历史可查，失败告警。
      
  en: >
      Daily full database backup with thirty days of retention, verifiable job history and alerts when a run fails.
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.757Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 538
    end_line: 538
apis:
  - protocol: file
    path: "backup/db/{date}/full.sql.gz"
    description:
      zh: >
          每日全量备份文件。
          
      en: >
          Daily full backup file.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/backup/jobs"
    description:
      zh: >
          触发或补做一次备份任务。
          
      en: >
          Triggers or reruns a backup job.
          
---
