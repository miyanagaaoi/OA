---
uid: 2920f449
id: oa.admin.org.staff.profile
parent: oa.admin.org.staff
name: {zh: "人员信息与账号", en: "User Profile & Account"}
description:
  zh: >
      新建与维护人员档案：账号唯一、密码加盐哈希、手机号加密存储、工号（水印用）、归属组织与公司；支持重置密码与在职状态调整。
      
  en: >
      Create and maintain user profiles: unique account, salted password hash, encrypted phone, employee number for watermarks, owning org and company; supports password reset and employment-status changes.
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.267Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/users"
    description:
      zh: >
          按组织/状态/关键字查询人员列表。
          
      en: >
          List users filtered by org, status or keyword.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/users"
    description:
      zh: >
          新建人员档案与账号（账号唯一校验）。
          
      en: >
          Create a user profile with unique-account validation.
          
  - protocol: http
    method: PUT
    path: "/api/v1/admin/users/{user_id}"
    description:
      zh: >
          修改人员信息与归属组织/公司。
          
      en: >
          Update user profile and owning org/company.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/users/{user_id}/reset-password"
    description:
      zh: >
          重置密码并强制下次登录修改。
          
      en: >
          Reset the password and force a change at next login.
          
deps:
  - kind: call
    to: oa.identity.user
    from_api: "POST /api/v1/admin/users"
    label: {zh: "复用身份域建档能力", en: "Reuse identity create"}
  - kind: dataflow
    to: oa.identity.user.profile
    to_api: "mysql:sys_user"
    label: {zh: "写入人员档案表", en: "Write user profile table"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-001`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_user`（§2. 身份与组织）
