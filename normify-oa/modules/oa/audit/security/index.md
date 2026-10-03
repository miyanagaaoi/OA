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
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.153Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-LOG-004`（§6.9 审计日志）
- `doc/data-model.md` → `CREATE TABLE sys_log`（§6. 签名、附件、抄送、消息、审计）
