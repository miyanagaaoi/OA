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
      
revision: 966907fad0c5f0d01bc6a76ddba80bbbf67f586a
updated_at: "2026-10-03T04:41:37.388Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-MSG-003`（§6.7 消息通知）
- `doc/data-model.md` → `CREATE TABLE flow_cc`（§6. 签名、附件、抄送、消息、审计）
