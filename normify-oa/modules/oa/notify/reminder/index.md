---
uid: 5a6a819b
id: oa.notify.reminder
parent: oa.notify
state: planned
name: {zh: "超时催办", en: "Timeout Reminders"}
description:
  zh: >
      节点超时检测（可配，最小 24h）后向审批人发站内信与邮件催办，可配置抄送其上级；补件时限超时仅催办发起人；一期不做自动跳过或自动升级。
      
  en: >
      Per-node timeout detection (configurable, at least 24 hours) sending in-app and email reminders, optionally copying the approver's superior; supplement deadlines remind the initiator only - never auto-skip and never auto-escalate.
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.636Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-007`（§6.4 流程引擎核心能力）
