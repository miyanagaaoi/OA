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
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.756Z"
fingerprint: 6be246c6834b9b56d4a42d5b955150bb6a06a766a90c031fd7f3af6b4c917a6d
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
