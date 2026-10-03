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
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.630Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/data-model.md"
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/data-model.md` → `CREATE TABLE flow_cc`（§6. 签名、附件、抄送、消息、审计）
- `doc/prd-0.1.md` → `REQ-MSG-003`（§6.7 消息通知）
