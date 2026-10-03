---
uid: bce4c942
id: oa.platform.backup.drill
parent: oa.platform.backup
state: planned
name: {zh: "恢复演练", en: "Restore Drills"}
description:
  zh: >
      每季度在隔离环境做一次恢复演练并留书面记录，验证 10 年审计留存的恢复能力。
      
  en: >
      A quarterly restore drill into an isolated environment with a written record, proving that the ten-year audit retention promise is actually recoverable.
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.399Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/admin/backup/restore-drills"
    description:
      zh: >
          登记一次季度恢复演练。
          
      en: >
          Records a quarterly restore drill.
          
  - protocol: file
    path: "reports/backup/restore-drill-{quarter}.md"
    description:
      zh: >
          恢复演练记录与结论。
          
      en: >
          Restore drill record and conclusions.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-NFR-008`（§第9章 非功能需求）
