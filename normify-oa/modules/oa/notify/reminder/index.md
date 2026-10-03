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
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.560Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-007`（§6.4 流程引擎核心能力）
