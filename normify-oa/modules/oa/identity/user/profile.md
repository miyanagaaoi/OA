---
uid: 0b5a7def
id: oa.identity.user.profile
parent: oa.identity.user
name: {zh: "人员档案与账号", en: "User Profile & Account"}
description:
  zh: >
      人员档案与登录账号：姓名、工号（水印使用）、主归属组织与公司、职务、在职状态（在职/停用/离职）、加盐密码哈希与加密手机号；手机号不以明文落库。
      
  en: >
      User profiles and login accounts: name, employee number (for watermarking), primary org and company, position, employment status, salted password hash and encrypted phone; phones are never stored in clear text.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.545Z"
fingerprint: 51a623413957badbbb0b541c1d6bb35b6d20b362c70912558c8ba724df4ced62
source:
  - path: "doc/data-model.md"
  - path: "doc/prd-0.1.md"
  - path: "oa-server/src/main/java/com/oa/identity/app/UserService.java"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/identity/users"
    description:
      zh: >
          查询人员列表（数据域过滤）。
          
      en: >
          Lists users.
          
  - protocol: http
    method: POST
    path: "/api/v1/identity/users"
    description:
      zh: >
          新建人员与账号（账号/工号判重为系统口径；初始口令仅本次返回）。
          
      en: >
          Creates a user and account; account/employee-no uniqueness is system-scope.
          
  - protocol: http
    method: PUT
    path: "/api/v1/identity/users/{id}"
    description:
      zh: >
          修改人员信息与在职状态（含停用闸门）。
          
      en: >
          Updates profile and employment status.
          
  - protocol: rpc
    path: "identity.user.updatePasswordHash"
    description:
      zh: >
          回写口令哈希（本人改密入口调用；只存 BCrypt 哈希，永不落明文）。
          
      en: >
          Writes a new password hash for a user (self-service change; hash only, never plaintext).
          
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

## 证据锚点
- `doc/data-model.md` → `CREATE TABLE sys_user`（§2. 身份与组织）
- `doc/prd-0.1.md` → `REQ-ADMIN-001`（§6.10 管理后台）
