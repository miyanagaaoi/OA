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
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.262Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `### 6.7 消息通知`（§6.7 消息通知）
- `doc/data-model.md` → `CREATE TABLE sys_message`（§6. 签名、附件、抄送、消息、审计）
