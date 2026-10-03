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
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.617Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-001`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_user`（§2. 身份与组织）
