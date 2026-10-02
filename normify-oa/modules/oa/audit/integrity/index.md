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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.642Z"
fingerprint: 4421092e639ef98e9cd4aac53b5975054268b604256d140e3544a8a3c2ba5551
source:
  - path: "doc/prd-0.1.md"
    line: 431
    end_line: 431
  - path: "doc/data-model.md"
    line: 739
    end_line: 799
---
