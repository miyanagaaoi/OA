---
uid: 8c885e47
id: oa.admin.report.timeout
parent: oa.admin.report
state: planned
name: {zh: "超时率统计", en: "Timeout Rate"}
description:
  zh: >
      统计超出配置超时时长的节点占比并列出超时节点明细，即一期口径的超时率报表。
      
  en: >
      Computes the share of nodes that exceeded their configured timeout and lists the offending nodes, which is the phase-one timeout-rate report.
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.644Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 439
    end_line: 439
  - path: "doc/prd-0.1.md"
    line: 550
    end_line: 550
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/reports/timeout-rate"
    description:
      zh: >
          按节点/公司/时间的超时率。
          
      en: >
          Timeout rate by node, company and period.
          
  - protocol: http
    method: GET
    path: "/api/v1/admin/reports/timeout-nodes"
    description:
      zh: >
          超时节点明细。
          
      en: >
          Detail of the nodes that timed out.
          
deps:
  - kind: reference
    to: oa.notify.reminder
    label: {zh: "超时催办记录", en: "Reminders on timeout"}
---
