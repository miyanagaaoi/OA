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
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.253Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
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
