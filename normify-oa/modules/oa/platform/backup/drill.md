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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.762Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 538
    end_line: 538
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
