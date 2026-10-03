---
uid: 6a798a2c
id: oa.audit.integrity
parent: oa.audit
state: planned
name: {zh: "不可篡改与保留期", en: "Immutability & Retention"}
description:
  zh: >
      不可篡改保障：日志只允许追加、数据库层拒绝修改与删除、哈希链路可检测篡改、保留期不得短于 10 年；系统管理员也不可删除审批单据与审计日志。
      
  en: >
      Immutability guardrails: logs only append, updates and deletes are refused at the database level, hash chaining detects tampering, retention is enforced at ten years or more, and administrators can never delete documents or audit entries.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.268Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-LOG-006`（§6.9 审计日志）
- `doc/data-model.md` → `### 8.1 审计与签名的不可变约束（对应 AC-20）`（§8.1 审计与签名的不可变约束）
