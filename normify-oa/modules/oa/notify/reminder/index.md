---
uid: 5a6a819b
id: oa.notify.reminder
parent: oa.notify
name: {zh: "超时催办", en: "Timeout Reminders"}
description:
  zh: >
      节点超时检测（可配，最小 24h）后向审批人发站内信与邮件催办，可配置抄送其上级；补件时限超时仅催办发起人；一期不做自动跳过或自动升级。
      
  en: >
      Per-node timeout detection (configurable, at least 24 hours) sending in-app and email reminders, optionally copying the approver's superior; supplement deadlines remind the initiator only - never auto-skip and never auto-escalate.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.092Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-007`（§6.4 流程引擎核心能力）
