---
uid: 0b5c9de3
id: oa.identity.user.directory
parent: oa.identity.user
name: {zh: "通讯录与手机号脱敏", en: "Directory & Phone Masking"}
description:
  zh: >
      通讯录查询与组织成员浏览：手机号默认脱敏（138****8888），仅本人与系统管理员可见完整值；列表与搜索遵循调用者的数据域边界。
      
  en: >
      Directory search and member browsing: phone numbers are masked by default (138****8888) and only the owner or a system admin sees the full value; lists and search respect the caller's data scope.
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.710Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 184
    end_line: 199
  - path: "doc/prd-0.1.md"
    line: 133
    end_line: 142
apis:
  - protocol: http
    method: GET
    path: "/api/v1/identity/directory"
    description:
      zh: >
          通讯录列表（手机号脱敏）。
          
      en: >
          Directory list with masked phones.
          
  - protocol: http
    method: GET
    path: "/api/v1/identity/users/{id}/phone"
    description:
      zh: >
          读取手机号（按角色脱敏或完整值）。
          
      en: >
          Reads a phone number masked or full by role.
          
  - protocol: http
    method: GET
    path: "/api/v1/identity/users/{id}/masked-profile"
    description:
      zh: >
          脱敏后的人员名片（供抄送人/协同部门浏览）。
          
      en: >
          Masked profile card for CC users and collaborating departments.
          
deps:
  - kind: call
    to: oa.identity.user.profile
    from_api: "GET /api/v1/identity/directory"
    to_api: "GET /api/v1/identity/users"
    label: {zh: "读取人员档案", en: "Read user profiles"}
  - kind: call
    to: oa.authz.visibility.field.contact
    from_api: "GET /api/v1/identity/users/{id}/phone"
    to_api: "rpc:authz.visibility.mask.phone"
    label: {zh: "应用手机号脱敏规则", en: "Apply phone masking"}
---
