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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.707Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
source:
  - path: "doc/prd-0.1.md"
    line: 407
    end_line: 407
  - path: "doc/data-model.md"
    line: 611
    end_line: 615
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
