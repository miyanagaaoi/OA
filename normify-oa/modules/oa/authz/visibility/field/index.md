---
uid: 7b167471
id: oa.authz.visibility.field
parent: oa.authz.visibility
name: {zh: "字段级限制", en: "Field-level Limits"}
description:
  zh: >
      一期硬编码的字段级规则：合同金额与资金金额对非财务角色只读且不可导出，手机号在通讯录默认脱敏；不做字段级白名单配置（列为 P2）。
      
  en: >
      Hard-coded phase-one field rules: contract and fund amounts are read-only and non-exportable for non-finance roles, and phone numbers are masked in the directory; no configurable field whitelist (P2).
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.293Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-AUTH-003`（§5.3 数据域口径）
