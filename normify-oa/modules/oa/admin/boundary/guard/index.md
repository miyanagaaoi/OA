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
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.179Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-006`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_log`（§6. 签名、附件、抄送、消息、审计）
