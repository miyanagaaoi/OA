---
uid: 2920f449
id: oa.admin.org.staff.profile
parent: oa.admin.org.staff
state: planned
name: {zh: "人员信息与账号", en: "User Profile & Account"}
description:
  zh: >
      新建与维护人员档案：账号唯一、密码加盐哈希、手机号加密存储、工号（水印用）、归属组织与公司；支持重置密码与在职状态调整。
      
  en: >
      Create and maintain user profiles: unique account, salted password hash, encrypted phone, employee number for watermarks, owning org and company; supports password reset and employment-status changes.
      
revision: e3b34a3c59417096ade647fab4261b06f6b605e2
updated_at: "2026-10-03T02:30:28.624Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/prd-0.1.md"
    line: 435
    end_line: 435
  - path: "doc/data-model.md"
    line: 59
    end_line: 83
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
