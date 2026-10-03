---
uid: 6a798a2a
id: oa.audit.trace
parent: oa.audit
state: planned
name: {zh: "审批轨迹", en: "Approval Trail"}
description:
  zh: >
      审批轨迹（面向展示）：每个节点的审批人、意见、签名图、时间，以及会签/或签模式下的各人结论，在单据详情页以时间线呈现。
      
  en: >
      Approval trace for display: per node the approver, opinion, signature image, time and each participant's conclusion under countersign or any-sign modes, rendered as a timeline on the document detail page.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.273Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-LOG-002`（§6.9 审计日志）
- `doc/data-model.md` → `CREATE TABLE sys_thread`（§6. 签名、附件、抄送、消息、审计）
