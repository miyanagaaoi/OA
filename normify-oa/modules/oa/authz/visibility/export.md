---
uid: 85da69eb
id: oa.authz.visibility.export
parent: oa.authz.visibility
name: {zh: "导出管控", en: "Export Control"}
description:
  zh: >
      导出功能仅系统管理员可用，金额字段对非财务角色不可导出；导出前统一鉴权，防止绕过界面直接调用接口导出。
      
  en: >
      Export is restricted to system admins and amount fields cannot be exported by non-finance roles; every export is authorised up front so the UI cannot be bypassed by calling the API directly.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:39.979Z"
fingerprint: 63fa18ff9ca66c0f45990c27a387c64888ebedfc529d3ac0d11ce477c6a02a5d
source:
  - path: "oa-server/src/main/java/com/oa/authz/visibility/ExportFieldPolicy.java"
  - path: "oa-server/src/main/java/com/oa/authz/visibility/ExportTarget.java"
  - path: "oa-server/src/main/java/com/oa/authz/api/ExportPolicyController.java"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/authz/export-check"
    description:
      zh: >
          导出前鉴权（角色 + 字段范围）。
          
      en: >
          Authorises an export by role and field.
          
  - protocol: http
    method: GET
    path: "/api/v1/authz/export-policy"
    description:
      zh: >
          读取各导出目标的权威列清单与限制。
          
      en: >
          Reads the export policy.
          
deps:
  - kind: call
    to: oa.authz.visibility.field.amount
    from_api: "POST /api/v1/authz/export-check"
    to_api: "rpc:authz.field.isAmountReadonly"
    label: {zh: "金额字段导出策略", en: "Check amount policy"}
  - kind: call
    to: oa.authz.rbac.effective
    from_api: "POST /api/v1/authz/export-check"
    to_api: "GET /api/v1/authz/effective-permissions"
    label: {zh: "仅系统管理员可导出", en: "Admin-only export"}
---
