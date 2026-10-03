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
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.256Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
    line: 407
    end_line: 407
  - path: "doc/data-model.md"
    line: 684
    end_line: 684
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
