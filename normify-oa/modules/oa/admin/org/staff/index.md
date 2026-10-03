---
uid: 07654bc1
id: oa.admin.org.staff
parent: oa.admin.org
state: planned
name: {zh: "人员档案维护", en: "Staff Records Admin"}
description:
  zh: >
      后台维护人员档案与账号、一人多岗任职与离职办理；离职前必须处理完名下全部待办，系统提示未处理任务数量并强制先转办或改派。
      
  en: >
      Console-side maintenance of user profiles and accounts, multi-post assignments and resignation; resignation is blocked until all of the user's tasks are handled, with the count surfaced and transfer/reassign enforced first.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.132Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-001`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_user`（§2. 身份与组织）
