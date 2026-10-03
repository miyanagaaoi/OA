---
uid: 6a798a2b
id: oa.audit.security
parent: oa.audit
state: planned
name: {zh: "权限变更与登录日志", en: "Security & Permission Logs"}
description:
  zh: >
      权限变更日志（角色、数据域、权限树勾选、流程模板发布，含变更前后值）与登录日志（登录时间、IP、设备信息、失败原因，保留 1 年）。
      
  en: >
      Permission-change log for roles, data scopes, permission-tree ticks and template publishing (with before/after values) and the login log of time, IP, device and failure reason retained for one year.
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.311Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-LOG-004`（§6.9 审计日志）
- `doc/data-model.md` → `CREATE TABLE sys_log`（§6. 签名、附件、抄送、消息、审计）
