---
uid: 59933a92
id: oa.notify.inbox.publish
parent: oa.notify.inbox
name: {zh: "站内信写入与类型", en: "In-App Message Publishing"}
description:
  zh: >
      站内信写入（sys_message）：待办产生、被驳回、被撤回、协同任务产生、超时催办、结果通知（通过/终止）按 msg_type 落库，携带 ref_instance_id 供跳转，并发布创建事件。
      
  en: >
      Writes in-app messages (sys_message) for new tasks, rejection, withdrawal, collaboration tasks, timeout reminders and results, keyed by msg_type with ref_instance_id for deep links, and publishes a creation event.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.321Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: mysql
    path: "sys_message"
    description:
      zh: >
          站内信表（msg_type / read_at / ref_instance_id）。
          
      en: >
          In-app message table (msg_type / read_at / ref_instance_id).
          
  - protocol: http
    method: POST
    path: "/api/v1/notifications/inbox"
    description:
      zh: >
          写入一条站内信（内部接口）。
          
      en: >
          Writes one in-app message (internal endpoint).
          
  - protocol: kafka
    path: "oa.notify.inbox.message-created"
    description:
      zh: >
          站内信创建事件。
          
      en: >
          Event published when an in-app message is created.
          
deps:
  - kind: call
    to: oa.notify.inbox.template
    from_api: "POST /api/v1/notifications/inbox"
    to_api: "GET /api/v1/notifications/inbox/templates"
    label: {zh: "套用文案模板", en: "Apply message template"}
  - kind: reference
    to: oa.identity.user
    label: {zh: "收件人", en: "Recipient"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-MSG-001`（§6.7 消息通知）
- `doc/data-model.md` → `CREATE TABLE sys_message`（§6. 签名、附件、抄送、消息、审计）
