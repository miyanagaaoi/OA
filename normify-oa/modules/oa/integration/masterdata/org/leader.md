---
uid: 30af54b0
id: oa.integration.masterdata.org.leader
parent: oa.integration.masterdata.org
state: planned
name: {zh: "节点负责人", en: "Org Node Leaders"}
description:
  zh: >
      查询某组织节点绑定的负责人及其类型（正职/副职）与排序，供下游系统路由单据时使用。
      
  en: >
      Leaders bound to an org node with their type (primary/deputy) and sort order, used by downstream systems to route documents.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.551Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/open/orgs/{id}/leaders"
    description:
      zh: >
          只读查询节点绑定的负责人列表。
          
      en: >
          Read-only list of the leaders bound to an org node.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `### 8.1 接口设计原则`（§8.1 接口设计原则）
