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
      
revision: 44fc7aba1c7e884ffa3553faf31bfce974b388a9
updated_at: "2026-10-03T04:12:20.638Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-006`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_log`（§6. 签名、附件、抄送、消息、审计）
