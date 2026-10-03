---
uid: 0f5ed48d
id: oa.identity.user.open-api
parent: oa.identity.user
state: planned
name: {zh: "人员只读查询接口", en: "User Read-only Query API"}
description:
  zh: >
      内部人员只读查询（姓名、工号、组织、在职状态），供门户、通讯录与集成面复用；手机号等敏感字段不外发（对外的 open 前缀由集成分支暴露）。
      
  en: >
      Internal read-only user queries (name, employee number, org, employment status) reused by the portal, directory and integration surface; sensitive fields such as phone numbers are excluded (the public open prefix belongs to the integration branch).
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.382Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/users"
    description:
      zh: >
          内部只读人员列表。
          
      en: >
          Internal read-only user list.
          
  - protocol: http
    method: GET
    path: "/api/v1/users/{id}"
    description:
      zh: >
          内部只读人员详情（无敏感字段）。
          
      en: >
          Internal read-only user detail without sensitive fields.
          
deps:
  - kind: call
    to: oa.identity.user.profile
    from_api: "GET /api/v1/users"
    to_api: "GET /api/v1/identity/users"
    label: {zh: "装配只读人员视图", en: "Build read-only view"}
---

## 证据锚点
- `doc/prd-0.1.md` → `### 8.1 接口设计原则`（§8.1 接口设计原则）
