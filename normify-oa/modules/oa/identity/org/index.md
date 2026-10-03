---
uid: 0b1f3c22
id: oa.identity.org
parent: oa.identity
name: {zh: "组织架构", en: "Organization Tree"}
description:
  zh: >
      集团-公司-部门-科室四级组织树：节点名称/类型/上级/负责人/状态与路径；停用前必须处理完该节点全部在途单据，停用后不可作为发起者归属节点。
      
  en: >
      The org tree (group/company/department/section) with node type, parent, leader, enable/disable state and materialised path; disable requires all in-flight documents to be finished first.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.359Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ORG-001`（§5.1 组织架构模型）
- `doc/data-model.md` → `CREATE TABLE sys_org`（§2. 身份与组织）
