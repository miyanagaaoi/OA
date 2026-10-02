---
uid: 1c2e4d35
id: oa.authz.visibility
parent: oa.authz
state: planned
name: {zh: "流转可见性与字段限制", en: "Routing Visibility & Field Limits"}
description:
  zh: >
      流转可见性：被指定为流转目标部门的负责人对在途单据可见，且仅限该单据、完结后保留历史可查，不得因此放宽同类单据可见性；同时实现字段级限制（合同/资金金额对非财务角色只读不可导出、手机号脱敏）。
  en: >
      Routing-chain visibility: a department named as a routing target can see that in-flight document (and keeps read-only history afterwards) without widening its scope for other documents; plus field-level limits (amounts read-only for non-finance, export for admins only, masked phones).
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T07:55:00Z"
fingerprint: pending
source:
  - path: "doc/prd-0.1.md"
    line: 184
    end_line: 199
---
