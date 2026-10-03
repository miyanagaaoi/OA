---
uid: 6a798a2a
id: oa.audit.trace
parent: oa.audit
name: {zh: "审批轨迹", en: "Approval Trail"}
description:
  zh: >
      审批轨迹（面向展示）：每个节点的审批人、意见、签名图、时间，以及会签/或签模式下的各人结论，在单据详情页以时间线呈现。
      
  en: >
      Approval trace for display: per node the approver, opinion, signature image, time and each participant's conclusion under countersign or any-sign modes, rendered as a timeline on the document detail page.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.231Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-LOG-002`（§6.9 审计日志）
- `doc/data-model.md` → `CREATE TABLE sys_thread`（§6. 签名、附件、抄送、消息、审计）
