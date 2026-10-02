---
uid: 85da69eb
id: oa.authz.visibility.export
parent: oa.authz.visibility
state: planned
name: {zh: "导出管控", en: "Export Control"}
description:
  zh: >
      导出功能仅系统管理员可用，金额字段对非财务角色不可导出；导出前统一鉴权，防止绕过界面直接调用接口导出。
      
  en: >
      Export is restricted to system admins and amount fields cannot be exported by non-finance roles; every export is authorised up front so the UI cannot be bypassed by calling the API directly.
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.656Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 192
    end_line: 199
  - path: "doc/prd-0.1.md"
    line: 546
    end_line: 561
apis:
  - protocol: http
    method: POST
    path: "/api/v1/authz/export-check"
    description:
      zh: >
          导出前鉴权（角色与字段范围）。
          
      en: >
          Authorises an export by role and field.
          
  - protocol: http
    method: GET
    path: "/api/v1/authz/export-policy"
    description:
      zh: >
          读取导出权限与字段限制策略。
          
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
