---
uid: 1c2e4d34
id: oa.authz.scope
parent: oa.authz
name: {zh: "数据域口径", en: "Data Scope Rules"}
description:
  zh: >
      可验收的数据域口径（V0.4）：普通员工仅本人相关单据、部门/科室负责人限本单位、子公司总经理限本公司、集团董事长与系统管理员见全集团；**集团职能部门（财务部）按「归口类别（资金/合同/印鉴）＋涉及费用的事项单＋流转链承接/经过的单据」可见，不涉及费用且未经流转的事项单不可见**（Q13）；以查询过滤实现强制隔离。
      
  en: >
      Verifiable data-scope rules (V0.4): employees see their own documents, department leaders their unit, subsidiary GM their company, chairman and admins everything; the group function department (Finance) sees its ownership categories (fund/contract/seal), cost-involving matter forms, and documents routed to or through it - matter forms without cost and without routing stay invisible. Enforced by query filtering.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.482Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-AUTH-001`（§5.2 权限模型）
- `doc/data-model.md` → `CREATE TABLE sys_role`（§3. 权限）
