---
uid: 258c1346
id: oa.audit.integrity.engine-parity
parent: oa.audit.integrity
name: {zh: "存储引擎不可变差异", en: "Engine Immutability Parity"}
description:
  zh: >
      描述 MySQL 触发器与 PostgreSQL 规则/REVOKE 两种不可变实现差异，并维护各引擎下发的 DDL 脚本，保证数据库迁移后只追加约束不丢失。
      
  en: >
      Captures the difference between MySQL triggers and PostgreSQL rules/REVOKE for immutability, and maintains the per-engine DDL scripts so append-only constraints survive a database migration.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.401Z"
fingerprint: d27f073aa0b7d919258285e22376dd379c55e1e74d7e28c48440b1100569087a
source:
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/audit/immutability/engine-profile"
    description:
      zh: >
          读取当前引擎的不可变实现档案。
          
      en: >
          Reads the immutability profile of the current engine.
          
  - protocol: file
    path: "deploy/sql/{engine}/immutability.sql"
    description:
      zh: >
          各引擎的不可变约束 DDL 脚本。
          
      en: >
          Per-engine DDL script for immutability constraints.
          
deps:
  - kind: reference
    to: oa.platform.deploy
    from_api: "GET /api/v1/audit/immutability/engine-profile"
    label: {zh: "数据库选型与 DDL 下发", en: "Database choice and DDL"}
---

## 证据锚点
- `doc/data-model.md` → `### 8.1 审计与签名的不可变约束（对应 AC-20）`（§8.1 审计与签名的不可变约束）
