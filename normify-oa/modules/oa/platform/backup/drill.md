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
      
revision: e3b34a3c59417096ade647fab4261b06f6b605e2
updated_at: "2026-10-03T02:47:41.779Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 558
    end_line: 558
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
