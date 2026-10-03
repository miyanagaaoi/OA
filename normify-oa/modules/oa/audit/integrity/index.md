---
uid: 6a798a2c
id: oa.audit.integrity
parent: oa.audit
name: {zh: "不可篡改与保留期", en: "Immutability & Retention"}
description:
  zh: >
      不可篡改保障：日志只允许追加、数据库层拒绝修改与删除、哈希链路可检测篡改、保留期不得短于 10 年；系统管理员也不可删除审批单据与审计日志。
      
  en: >
      Immutability guardrails: logs only append, updates and deletes are refused at the database level, hash chaining detects tampering, retention is enforced at ten years or more, and administrators can never delete documents or audit entries.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:39.937Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-LOG-006`（§6.9 审计日志）
- `doc/data-model.md` → `### 8.1 审计与签名的不可变约束（对应 AC-20）`（§8.1 审计与签名的不可变约束）
