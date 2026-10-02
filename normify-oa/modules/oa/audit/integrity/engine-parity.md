---
uid: 258c1346
id: oa.audit.integrity.engine-parity
parent: oa.audit.integrity
state: planned
name: {zh: "存储引擎不可变差异", en: "Engine Immutability Parity"}
description:
  zh: >
      描述 MySQL 触发器与 PostgreSQL 规则/REVOKE 两种不可变实现差异，并维护各引擎下发的 DDL 脚本，保证数据库迁移后只追加约束不丢失。
      
  en: >
      Captures the difference between MySQL triggers and PostgreSQL rules/REVOKE for immutability, and maintains the per-engine DDL scripts so append-only constraints survive a database migration.
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.681Z"
fingerprint: affe2755b0ec3d7b6adffe3259b4864634f794b5be85e144d9ec84d32aeba3d7
source:
  - path: "doc/data-model.md"
    line: 800
    end_line: 814
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
