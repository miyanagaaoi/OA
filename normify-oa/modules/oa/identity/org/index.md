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
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.298Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ORG-001`（§5.1 组织架构模型）
- `doc/data-model.md` → `CREATE TABLE sys_org`（§2. 身份与组织）
