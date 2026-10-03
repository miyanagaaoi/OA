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
      
revision: e3b34a3c59417096ade647fab4261b06f6b605e2
updated_at: "2026-10-03T02:47:41.779Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 201
    end_line: 201
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
