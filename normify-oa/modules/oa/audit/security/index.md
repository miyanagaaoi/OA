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
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.271Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-LOG-004`（§6.9 审计日志）
- `doc/data-model.md` → `CREATE TABLE sys_log`（§6. 签名、附件、抄送、消息、审计）
