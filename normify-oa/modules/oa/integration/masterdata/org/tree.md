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
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.253Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
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
