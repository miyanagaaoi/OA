---
uid: 5f6a8197
id: oa.notify
parent: oa
name: {zh: "消息通知", en: "Notifications"}
description:
  zh: >
      一期仅两种渠道：站内信与邮件（不做移动端推送、不对接企业微信/钉钉）。覆盖待办产生、驳回、撤回、协同任务、超时催办与结果通知，另有抄送（只读可见、不产生待办）。
      
  en: >
      Two channels only in phase one: in-app messages and email (no push, no WeCom/DingTalk). Covers new tasks, rejection, withdrawal, collaboration tasks, timeout reminders and results, plus read-only CC.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.323Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `### 6.7 消息通知`（§6.7 消息通知）
- `doc/data-model.md` → `CREATE TABLE sys_message`（§6. 签名、附件、抄送、消息、审计）
