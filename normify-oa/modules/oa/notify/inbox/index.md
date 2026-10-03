---
uid: 5a6a8198
id: oa.notify.inbox
parent: oa.notify
state: planned
name: {zh: "站内信", en: "In-App Inbox"}
description:
  zh: >
      站内信收件箱：待办产生、被驳回、被撤回、协同任务产生、超时催办与结果通知（通过/终止）均写入；支持未读计数、标记已读与跳回单据详情。
      
  en: >
      In-app message inbox: generated on new tasks, rejection, withdrawal, collaboration task creation, timeout reminders and results; supports unread counts, mark-as-read and deep links back to the document.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.259Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-MSG-001`（§6.7 消息通知）
- `doc/data-model.md` → `CREATE TABLE sys_message`（§6. 签名、附件、抄送、消息、审计）
