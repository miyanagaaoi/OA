---
uid: 07654bc1
id: oa.admin.org.staff
parent: oa.admin.org
name: {zh: "人员档案维护", en: "Staff Records Admin"}
description:
  zh: >
      后台维护人员档案与账号、一人多岗任职与离职办理；离职前必须处理完名下全部待办，系统提示未处理任务数量并强制先转办或改派。
      
  en: >
      Console-side maintenance of user profiles and accounts, multi-post assignments and resignation; resignation is blocked until all of the user's tasks are handled, with the count surfaced and transfer/reassign enforced first.
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.266Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-001`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_user`（§2. 身份与组织）
