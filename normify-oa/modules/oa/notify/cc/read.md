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
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.379Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
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
