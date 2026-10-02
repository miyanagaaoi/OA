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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.705Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
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
