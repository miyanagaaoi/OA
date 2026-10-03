---
uid: 5f6a8197
id: oa.notify
parent: oa
state: planned
name: {zh: "消息通知", en: "Notifications"}
description:
  zh: >
      一期仅两种渠道：站内信与邮件（不做移动端推送、不对接企业微信/钉钉）。覆盖待办产生、驳回、撤回、协同任务、超时催办与结果通知，另有抄送（只读可见、不产生待办）。
      
  en: >
      Two channels only in phase one: in-app messages and email (no push, no WeCom/DingTalk). Covers new tasks, rejection, withdrawal, collaboration tasks, timeout reminders and results, plus read-only CC.
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.633Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `### 6.7 消息通知`（§6.7 消息通知）
- `doc/data-model.md` → `CREATE TABLE sys_message`（§6. 签名、附件、抄送、消息、审计）
