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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.633Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
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
