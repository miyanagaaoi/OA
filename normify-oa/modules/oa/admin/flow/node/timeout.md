---
uid: 533ac317
id: oa.admin.flow.node.timeout
parent: oa.admin.flow.node
name: {zh: "超时时长", en: "Node Timeout"}
description:
  zh: >
      配置可选的节点超时时长（最小 24 小时）；超时后仅向审批人发站内信与邮件催办，一期不做自动跳过或自动升级。
      
  en: >
      Configures an optional per-node timeout with a 24-hour minimum; on expiry the system only sends in-app and mail reminders, with no automatic skip or escalation in phase one.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:39.884Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-007`（§6.4 流程引擎核心能力）
