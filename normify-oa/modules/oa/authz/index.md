---
uid: 1b2e4d32
id: oa.authz
parent: oa
name: {zh: "权限与数据域", en: "Authorization & Data Scope"}
description:
  zh: >
      RBAC 角色与权限树 + 数据域双层模型：子公司数据隔离、集团职能部门按「归口类别 + 流转链」可见（V0.4 Q13）、流转链可见性、字段级限制（金额只读，导出仅系统管理员与财务角色，手机号脱敏）。
      
  en: >
      RBAC roles/permission tree plus a data-scope layer: subsidiary isolation, group function-department visibility by ownership category and routing chain (Q13), routing-chain visibility, and field-level limits (read-only amounts with export limited to admins and Finance, masked phones).
      
revision: 966907fad0c5f0d01bc6a76ddba80bbbf67f586a
updated_at: "2026-10-03T04:41:37.319Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `### 5.2 权限模型（REQ-AUTH-001 数据隔离、REQ-AUTH-002 越权拒绝）`（§5.2 权限模型）
- `doc/data-model.md` → `CREATE TABLE sys_role`（§3. 权限）
