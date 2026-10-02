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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.706Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 403
    end_line: 407
  - path: "doc/prd-0.1.md"
    line: 411
    end_line: 411
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
