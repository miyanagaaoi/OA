---
uid: 60798a28
id: oa.audit
parent: oa
name: {zh: "审计与留痕", en: "Audit & Traceability"}
description:
  zh: >
      操作日志、审批轨迹、登录日志与权限变更日志；全部只追加、禁止修改删除，审计日志与审批轨迹保留不少于 10 年、登录日志 1 年，并保证哈希链路的完整性。
      
  en: >
      Operation logs, approval traces, login logs and permission-change logs: append-only, non-deletable, retained ten years or more (login logs one year), with integrity hashing.
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.277Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `### 6.9 审计日志`（§6.9 审计日志）
- `doc/data-model.md` → `CREATE TABLE sys_log`（§6. 签名、附件、抄送、消息、审计）
