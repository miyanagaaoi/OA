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
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.631Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
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
