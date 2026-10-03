---
uid: b5f3e9dd
id: oa.platform.backup.daily
parent: oa.platform.backup
name: {zh: "每日全量备份", en: "Daily Full Backup"}
description:
  zh: >
      数据库每日全量备份、保留 30 天，任务历史可查，失败告警。
      
  en: >
      Daily full database backup with thirty days of retention, verifiable job history and alerts when a run fails.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.497Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-NFR-008`（§第9章 非功能需求）
