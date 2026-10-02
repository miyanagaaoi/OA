---
uid: 7c3b7cb5
id: oa.notify.cc.read
parent: oa.notify.cc
state: planned
name: {zh: "抄送已读回执", en: "CC Read Receipt"}
description:
  zh: >
      抄送人打开单据即回写 flow_cc.read_at，供发起人与审计查看谁已阅；回执不触发催办、不产生待办。
      
  en: >
      Opening the document writes flow_cc.read_at so the initiator and audit can see who read it; receipts trigger no reminder and no task.
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.754Z"
fingerprint: 6be246c6834b9b56d4a42d5b955150bb6a06a766a90c031fd7f3af6b4c917a6d
source:
  - path: "doc/data-model.md"
    line: 598
    end_line: 602
  - path: "doc/prd-0.1.md"
    line: 409
    end_line: 409
apis:
  - protocol: http
    method: PUT
    path: "/api/v1/notifications/cc/{id}/read"
    description:
      zh: >
          抄送已读回执。
          
      en: >
          Writes the CC read receipt.
          
  - protocol: http
    method: GET
    path: "/api/v1/instances/{instance_id}/cc"
    description:
      zh: >
          抄送人列表与已读状态。
          
      en: >
          Lists CC recipients with read state.
          
deps:
  - kind: reference
    to: oa.portal.detail
    label: {zh: "详情页进入即回执", en: "Receipt on detail view"}
---
