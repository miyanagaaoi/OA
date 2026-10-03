---
uid: 1c2e4d35
id: oa.authz.visibility
parent: oa.authz
name: {zh: "流转可见性与字段限制", en: "Routing Visibility & Field Limits"}
description:
  zh: >
      流转可见性：被指定为流转目标部门的负责人对在途单据可见，且仅限该单据、完结后保留历史可查，不得因此放宽同类单据可见性；同时实现字段级限制（合同/资金金额对非财务角色只读不可导出、手机号脱敏）。
      
  en: >
      Routing-chain visibility: a department named as a routing target can see that in-flight document (and keeps read-only history afterwards) without widening its scope for other documents; plus field-level limits (amounts read-only for non-finance, export for admins only, masked phones).
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:39.981Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-AUTH-004`（§5.3 数据域口径）
