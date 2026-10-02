---
uid: 607f1fba
id: oa.notify.inbox.read
parent: oa.notify.inbox
state: planned
name: {zh: "未读计数与已读标记", en: "Unread Count & Mark-as-Read"}
description:
  zh: >
      未读站内信计数（导航红点与工作台角标）与单条/全部标记已读，写入 read_at；未读优先影响列表排序。
  en: >
      Unread counts for nav badges and the workbench plus single or bulk mark-as-read writing read_at; unread state drives list ordering.
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:10:00Z"
fingerprint: pending
source:
  - path: "doc/prd-0.1.md"
    line: 407
    end_line: 411
  - path: "doc/data-model.md"
    line: 617
    end_line: 620
apis:
  - protocol: http
    method: GET
    path: "/api/v1/notifications/inbox/unread-count"
    description:
      zh: >
          查询未读站内信数量。
      en: >
          Returns the number of unread in-app messages.
  - protocol: http
    method: PUT
    path: "/api/v1/notifications/inbox/{id}/read"
    description:
      zh: >
          标记单条站内信已读。
      en: >
          Marks one message as read.
  - protocol: http
    method: PUT
    path: "/api/v1/notifications/inbox/read-all"
    description:
      zh: >
          一键全部标记已读。
      en: >
          Marks all messages as read.
deps:
  - kind: reference
    to: oa.portal.workbench
    label: {zh: "工作台未读角标", en: "Workbench unread badge"}
---
