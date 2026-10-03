---
uid: de52a111
id: oa.admin.boundary.settings.retention
parent: oa.admin.boundary.settings
state: planned
name: {zh: "审计保留期", en: "Audit Retention"}
description:
  zh: >
      可配置项：审计日志与审批轨迹保留期（不得短于 10 年）、登录日志保留期（1 年）；配置值低于下限时拒绝保存。
      
  en: >
      Adjustable items: retention of audit logs and approval traces (never shorter than ten years) and of login logs (one year); values below the floor are refused on save.
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.187Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/prd-0.1.md"
    line: 560
    end_line: 560
  - path: "doc/data-model.md"
    line: 828
    end_line: 828
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/retention-policy"
    description:
      zh: >
          查询审计与登录日志保留期。
          
      en: >
          Read the audit and login log retention values.
          
  - protocol: http
    method: PUT
    path: "/api/v1/admin/retention-policy"
    description:
      zh: >
          设置保留期；低于下限的值拒绝保存。
          
      en: >
          Set retention; values below the floor are refused.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/retention-policy/check"
    description:
      zh: >
          校验配置值是否不低于不可商议的下限。
          
      en: >
          Check a value against the non-negotiable floor.
          
deps:
  - kind: reference
    to: oa.audit.integrity
    from_api: "POST /api/v1/admin/retention-policy/check"
    label: {zh: "保留期下限由审计校验", en: "Retention floor check"}
  - kind: reference
    to: oa.archive.policy
    label: {zh: "归档保留期衔接", en: "Align with archive policy"}
---
