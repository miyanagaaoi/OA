---
uid: 0b1f3c23
id: oa.identity.user
parent: oa.identity
name: {zh: "人员与通讯录", en: "Users & Directory"}
description:
  zh: >
      人员档案与账号、通讯录（手机号默认脱敏）、工号、在职状态、Excel 批量导入导出；离职前必须处理完名下全部待办，系统提示未处理任务数量并强制先转办或改派。
      
  en: >
      User profiles, accounts, contact directory and Excel import/export; mobile numbers are masked by default, and resignation is blocked until all of the user's tasks are handled.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.308Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-001`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_user`（§2. 身份与组织）
