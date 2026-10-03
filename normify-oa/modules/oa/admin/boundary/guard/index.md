---
uid: b92e350a
id: oa.admin.boundary.guard
parent: oa.admin.boundary
state: planned
name: {zh: "不可删改硬闸门", en: "Immutable Guard Gates"}
description:
  zh: >
      不可删改硬闸门：阻止系统管理员删除已产生的审批单据与审计日志、阻止修改已审批通过单据的审批结果，被拒动作均留痕。
      
  en: >
      Immutable gates that stop the super-admin from deleting produced approval documents and audit logs or changing an approved verdict; every rejected attempt is logged.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.113Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-006`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_log`（§6. 签名、附件、抄送、消息、审计）
