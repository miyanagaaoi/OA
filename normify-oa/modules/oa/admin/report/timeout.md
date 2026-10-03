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
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.392Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
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
