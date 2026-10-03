---
uid: 5e511ff5
id: oa.notify.inbox.template
parent: oa.notify.inbox
state: planned
name: {zh: "站内信文案模板", en: "Inbox Message Templates"}
description:
  zh: >
      按 msg_type 维护站内信标题与内容模板（单据标题、单号、节点名、意见等占位符），发送前统一渲染，避免各触发点各写一段文案。
      
  en: >
      Keeps title and body templates per msg_type (placeholders for document title, business number, node name, opinion), rendered centrally before sending.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.381Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/notifications/inbox/templates"
    description:
      zh: >
          列出站内信文案模板。
          
      en: >
          Lists the in-app message templates.
          
  - protocol: http
    method: PUT
    path: "/api/v1/notifications/inbox/templates/{msg_type}"
    description:
      zh: >
          维护指定类型的模板文案。
          
      en: >
          Maintains the template of one msg_type.
          
deps:
  - kind: reference
    to: oa.admin.dict
    label: {zh: "事项类别等字典占位符", en: "Dictionary placeholders"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-MSG-001`（§6.7 消息通知）
- `doc/data-model.md` → `CREATE TABLE sys_message`（§6. 签名、附件、抄送、消息、审计）
