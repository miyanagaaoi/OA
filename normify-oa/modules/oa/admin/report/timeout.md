---
uid: 8c885e47
id: oa.admin.report.timeout
parent: oa.admin.report
name: {zh: "超时率统计", en: "Timeout Rate"}
description:
  zh: >
      统计超出配置超时时长的节点占比并列出超时节点明细，即一期口径的超时率报表。
      
  en: >
      Computes the share of nodes that exceeded their configured timeout and lists the offending nodes, which is the phase-one timeout-rate report.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:39.914Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-005`（§6.10 管理后台）
