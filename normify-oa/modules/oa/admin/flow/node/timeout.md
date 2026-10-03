---
uid: 533ac317
id: oa.admin.flow.node.timeout
parent: oa.admin.flow.node
state: planned
name: {zh: "超时时长", en: "Node Timeout"}
description:
  zh: >
      配置可选的节点超时时长（最小 24 小时）；超时后仅向审批人发站内信与邮件催办，一期不做自动跳过或自动升级。
      
  en: >
      Configures an optional per-node timeout with a 24-hour minimum; on expiry the system only sends in-app and mail reminders, with no automatic skip or escalation in phase one.
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.197Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 351
    end_line: 351
  - path: "doc/prd-0.1.md"
    line: 550
    end_line: 550
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/flow-nodes/{node_id}/timeout"
    description:
      zh: >
          查询节点的超时配置。
          
      en: >
          Read the timeout configuration of a node.
          
  - protocol: http
    method: PUT
    path: "/api/v1/admin/flow-nodes/{node_id}/timeout"
    description:
      zh: >
          设置超时时长（最小 24 小时，仅催办）。
          
      en: >
          Set a timeout of at least 24 hours; reminders only.
          
deps:
  - kind: event
    to: oa.notify.reminder
    from_api: "PUT /api/v1/admin/flow-nodes/{node_id}/timeout"
    label: {zh: "超时触发催办", en: "Timeout drives reminders"}
---
