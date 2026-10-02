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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.761Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
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
