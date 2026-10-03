---
uid: 11c21b54
id: oa.integration.masterdata.org.tree
parent: oa.integration.masterdata.org
state: planned
name: {zh: "组织树遍历", en: "Org Tree Traversal"}
description:
  zh: >
      从指定节点或根节点遍历组织树，可按节点类型（集团/公司/部门/科室）与启用状态过滤。
      
  en: >
      Walks the org tree from a given node or the root, filtered by node type (group/company/department/section) and status.
      
revision: 44fc7aba1c7e884ffa3553faf31bfce974b388a9
updated_at: "2026-10-03T04:12:20.745Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/open/orgs"
    description:
      zh: >
          只读组织树遍历，可按上级、类型与状态过滤。
          
      en: >
          Read-only org tree traversal filtered by parent, type and status.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `### 8.1 接口设计原则`（§8.1 接口设计原则）
