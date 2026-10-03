---
uid: 5a6a819a
id: oa.notify.cc
parent: oa.notify
state: planned
name: {zh: "抄送", en: "CC Recipients"}
description:
  zh: >
      抄送：发起人自选 + 流程模板固定抄送；抄送人只读可见、不产生待办，记录已读时间。
      
  en: >
      CC recipients chosen by the initiator plus fixed CC entries from the template; they see the document read-only, produce no tasks, and read state is tracked.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.379Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-MSG-003`（§6.7 消息通知）
- `doc/data-model.md` → `CREATE TABLE flow_cc`（§6. 签名、附件、抄送、消息、审计）
