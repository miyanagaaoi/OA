---
uid: 1c5456aa
id: oa.audit.integrity.immutable
parent: oa.audit.integrity
state: planned
name: {zh: "只追加约束", en: "Append-only Guard"}
description:
  zh: >
      应用层禁用 UPDATE/DELETE，并在数据库层用触发器对审计日志与签名记录强制拒绝修改和删除，形成双保险的不可篡改基线。
      
  en: >
      Application layer forbids UPDATE/DELETE while database triggers reject modification and deletion of audit logs and signature records, forming a two-layer immutability baseline.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.454Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/data-model.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/audit/immutability/assert"
    description:
      zh: >
          校验目标表是否处于只追加保护下。
          
      en: >
          Asserts that a target table is under append-only protection.
          
  - protocol: http
    method: GET
    path: "/api/v1/audit/immutability/constraints"
    description:
      zh: >
          列出不可变约束与触发器清单。
          
      en: >
          Lists immutability constraints and triggers.
          
deps:
  - kind: reference
    to: oa.audit.oplog.capture
    from_api: "POST /api/v1/audit/immutability/assert"
    label: {zh: "保护操作日志只追加", en: "Guard the operation log"}
  - kind: reference
    to: oa.sign.record
    from_api: "POST /api/v1/audit/immutability/assert"
    label: {zh: "保护签名记录只追加", en: "Guard signature records"}
  - kind: reference
    to: oa.admin.boundary
    from_api: "GET /api/v1/audit/immutability/constraints"
    label: {zh: "管理员不可删除日志", en: "Admins cannot delete logs"}
---

## 证据锚点
- `doc/data-model.md` → `### 8.1 审计与签名的不可变约束（对应 AC-20）`（§8.1 审计与签名的不可变约束）
- `doc/prd-0.1.md` → `REQ-LOG-006`（§6.9 审计日志）
