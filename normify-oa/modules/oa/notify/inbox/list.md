---
uid: "623e2223"
id: oa.notify.inbox.list
parent: oa.notify.inbox
state: planned
name: {zh: "收件箱列表与跳转", en: "Inbox List & Deep Link"}
description:
  zh: >
      站内信分页列表（未读优先、时间倒序）与详情，点击跳回单据详情页；一期无移动端推送通道，站内信需要用户主动进入系统查看。
      
  en: >
      Paged inbox list (unread first, newest first) and detail with a deep link back to the document; phase one has no mobile push, so the user must open the system.
      
revision: 966907fad0c5f0d01bc6a76ddba80bbbf67f586a
updated_at: "2026-10-03T04:41:37.390Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/notifications/inbox"
    description:
      zh: >
          站内信分页列表。
          
      en: >
          Paged list of in-app messages.
          
  - protocol: http
    method: GET
    path: "/api/v1/notifications/inbox/{id}"
    description:
      zh: >
          站内信详情（含跳转标识）。
          
      en: >
          Message detail with its document reference.
          
deps:
  - kind: reference
    to: oa.portal.detail
    label: {zh: "跳回单据详情", en: "Deep link to document"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-MSG-001`（§6.7 消息通知）
