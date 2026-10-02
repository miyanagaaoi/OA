---
uid: 0b5a7def
id: oa.identity.user.profile
parent: oa.identity.user
state: planned
name: {zh: "人员档案与账号", en: "User Profile & Account"}
description:
  zh: >
      人员档案与登录账号：姓名、工号（水印使用）、主归属组织与公司、职务、在职状态（在职/停用/离职）、加盐密码哈希与加密手机号；手机号不以明文落库。
      
  en: >
      User profiles and login accounts: name, employee number (for watermarking), primary org and company, position, employment status, salted password hash and encrypted phone; phones are never stored in clear text.
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.739Z"
fingerprint: 6be246c6834b9b56d4a42d5b955150bb6a06a766a90c031fd7f3af6b4c917a6d
source:
  - path: "doc/data-model.md"
    line: 59
    end_line: 83
  - path: "doc/prd-0.1.md"
    line: 133
    end_line: 142
apis:
  - protocol: http
    method: GET
    path: "/api/v1/identity/users"
    description:
      zh: >
          查询人员列表。
          
      en: >
          Lists users.
          
  - protocol: http
    method: POST
    path: "/api/v1/identity/users"
    description:
      zh: >
          新建人员与账号。
          
      en: >
          Creates a user and account.
          
  - protocol: http
    method: PUT
    path: "/api/v1/identity/users/{id}"
    description:
      zh: >
          修改人员信息与在职状态。
          
      en: >
          Updates profile and employment status.
          
  - protocol: http
    method: PUT
    path: "/api/v1/identity/users/{id}/password"
    description:
      zh: >
          重设密码（写入加盐哈希）。
          
      en: >
          Resets the password hash.
          
  - protocol: mysql
    path: "sys_user"
    description:
      zh: >
          用户表。
          
      en: >
          User table.
          
deps:
  - kind: call
    to: oa.identity.org.node
    from_api: "POST /api/v1/identity/users"
    to_api: "GET /api/v1/identity/orgs/tree"
    label: {zh: "校验归属组织与公司", en: "Validate parent org"}
---
