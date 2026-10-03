---
uid: 68e0c873
id: oa.authz.scope.isolation
parent: oa.authz.scope
name: {zh: "跨公司与越权拦截", en: "Isolation & Denial"}
description:
  zh: >
      子公司数据隔离与越权拦截：列表与搜索中不出现越权单据，直接构造 URL 访问返回无权限（非空页）；越权尝试写入安全审计。
      
  en: >
      Subsidiary isolation and unauthorized-access blocking: out-of-scope documents never appear in lists or search, direct URL access returns a permission error rather than an empty page, and attempts are written to the security audit.
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.291Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: rpc
    path: "authz.scope.assertAccess"
    description:
      zh: >
          断言当前用户对目标单据有访问权。
          
      en: >
          Asserts access to a target document.
          
  - protocol: http
    method: GET
    path: "/api/v1/authz/scope-check"
    description:
      zh: >
          返回当前用户对指定资源的判定结果。
          
      en: >
          Returns the access verdict for a resource.
          
deps:
  - kind: call
    to: oa.authz.scope.filter
    from_api: "rpc:authz.scope.assertAccess"
    to_api: "rpc:authz.scope.buildFilter"
    label: {zh: "生成拒访判定条件", en: "Build denial filter"}
  - kind: dataflow
    to: oa.audit.security
    label: {zh: "越权访问尝试留痕", en: "Log denied access"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-AUTH-002`（§5.2 权限模型）
