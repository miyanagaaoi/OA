---
uid: 5a6a819a
id: oa.notify.cc
parent: oa.notify
name: {zh: "抄送", en: "CC Recipients"}
description:
  zh: >
      抄送：发起人自选 + 流程模板固定抄送；抄送人只读可见、不产生待办，记录已读时间。
      
  en: >
      CC recipients chosen by the initiator plus fixed CC entries from the template; they see the document read-only, produce no tasks, and read state is tracked.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.490Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-MSG-003`（§6.7 消息通知）
- `doc/data-model.md` → `CREATE TABLE flow_cc`（§6. 签名、附件、抄送、消息、审计）
