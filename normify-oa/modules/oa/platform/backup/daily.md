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
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.761Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
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
