---
uid: 351652c8
id: oa.sign.record.immutable
parent: oa.sign.record
name: {zh: "不可变约束", en: "Immutability Enforcement"}
description:
  zh: >
      签名记录的不可变约束：数据库触发器拒绝 UPDATE/DELETE（仅放行二期验签结果回写）、应用层仓储禁止修改删除，并以自检接口证明无绕过通路，对应验收项 AC-20。
      
  en: >
      Immutability of signature records: database triggers reject UPDATE/DELETE (only the CA verify-result write-back passes), the repository layer forbids mutation, and a self-check proves there is no bypass (AC-20).
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.397Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/data-model.md"
  - path: "doc/prd-0.1.md"
deps:
  - kind: reference
    to: oa.sign.record.append
    to_api: "mysql:flow_signature"
    label: {zh: "约束签名记录写入路径", en: "Constrains record writes"}
---

## 证据锚点
- `doc/data-model.md` → `### 8.1 审计与签名的不可变约束（对应 AC-20）`（§8.1 审计与签名的不可变约束）
- `doc/prd-0.1.md` → `REQ-SIGN-004`（§6.5 电子签名与身份确认）
