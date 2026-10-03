---
uid: "799120e1"
id: oa.notify.cc.dispatch
parent: oa.notify.cc
state: planned
name: {zh: "抄送落库与可见性", en: "CC Dispatch & Visibility"}
description:
  zh: >
      汇总发起人自选与模板固定抄送人，按 (instance_id, user_id) 唯一键去重后写入 flow_cc（source 标记来源），赋予只读可见性；抄送不进入审批链、不产生待办。
      
  en: >
      Merges and de-duplicates initiator-selected and template-fixed recipients into flow_cc (source marks the origin) and grants read-only visibility; CC never enters the approval chain.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.554Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: mysql
    path: "flow_cc"
    description:
      zh: >
          抄送表（source / read_at，唯一键 instance_id+user_id）。
          
      en: >
          CC table (source / read_at, unique instance+user).
          
  - protocol: http
    method: POST
    path: "/api/v1/notifications/cc/resolve"
    description:
      zh: >
          合并抄送人并去重落库。
          
      en: >
          Merges CC recipients and de-duplicates before storing.
          
  - protocol: kafka
    path: "oa.notify.cc.created"
    description:
      zh: >
          抄送建立事件。
          
      en: >
          Event published when CC recipients are created.
          
deps:
  - kind: reference
    to: oa.authz.visibility
    label: {zh: "抄送人只读可见性", en: "CC read-only visibility"}
  - kind: call
    to: oa.notify.inbox.publish
    from_api: "POST /api/v1/notifications/cc/resolve"
    to_api: "POST /api/v1/notifications/inbox"
    label: {zh: "生成抄送站内信", en: "Create CC inbox message"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-MSG-003`（§6.7 消息通知）
- `doc/data-model.md` → `CREATE TABLE flow_cc`（§6. 签名、附件、抄送、消息、审计）
