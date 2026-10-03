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
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.566Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-AUTH-003`（§5.3 数据域口径）
