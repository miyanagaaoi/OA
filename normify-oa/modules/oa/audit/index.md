---
uid: 60798a28
id: oa.audit
parent: oa
state: planned
name: {zh: "审计与留痕", en: "Audit & Traceability"}
description:
  zh: >
      操作日志、审批轨迹、登录日志与权限变更日志；全部只追加、禁止修改删除，审计日志与审批轨迹保留不少于 10 年、登录日志 1 年，并保证哈希链路的完整性。
      
  en: >
      Operation logs, approval traces, login logs and permission-change logs: append-only, non-deletable, retained ten years or more (login logs one year), with integrity hashing.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.452Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `### 6.9 审计日志`（§6.9 审计日志）
- `doc/data-model.md` → `CREATE TABLE sys_log`（§6. 签名、附件、抄送、消息、审计）
