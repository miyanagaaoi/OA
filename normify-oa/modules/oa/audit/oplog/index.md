---
uid: 6a798a29
id: oa.audit.oplog
parent: oa.audit
name: {zh: "操作日志", en: "Operation Log"}
description:
  zh: >
      操作日志：谁、何时、从何 IP、对哪个单据/配置执行了什么操作，配置类变更同时记录变更前后值；只追加、保留不少于 10 年。
      
  en: >
      Operation log recording who did what, when, from which IP, against which document or configuration, including before/after values for configuration changes; append-only for ten years or more.
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.281Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-LOG-001`（§6.9 审计日志）
- `doc/data-model.md` → `CREATE TABLE sys_log`（§6. 签名、附件、抄送、消息、审计）
