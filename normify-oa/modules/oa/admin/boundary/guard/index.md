---
uid: b92e350a
id: oa.admin.boundary.guard
parent: oa.admin.boundary
name: {zh: "不可删改硬闸门", en: "Immutable Guard Gates"}
description:
  zh: >
      不可删改硬闸门：阻止系统管理员删除已产生的审批单据与审计日志、阻止修改已审批通过单据的审批结果，被拒动作均留痕。
      
  en: >
      Immutable gates that stop the super-admin from deleting produced approval documents and audit logs or changing an approved verdict; every rejected attempt is logged.
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.250Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-006`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_log`（§6. 签名、附件、抄送、消息、审计）
