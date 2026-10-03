---
uid: de52a111
id: oa.admin.boundary.settings.retention
parent: oa.admin.boundary.settings
name: {zh: "审计保留期", en: "Audit Retention"}
description:
  zh: >
      可配置项：审计日志与审批轨迹保留期（不得短于 10 年）、登录日志保留期（1 年）；配置值低于下限时拒绝保存。
      
  en: >
      Adjustable items: retention of audit logs and approval traces (never shorter than ten years) and of login logs (one year); values below the floor are refused on save.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.375Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-NFR-007`（§6.9 审计日志）
- `doc/data-model.md` → `CREATE TABLE sys_log`（§6. 签名、附件、抄送、消息、审计）
