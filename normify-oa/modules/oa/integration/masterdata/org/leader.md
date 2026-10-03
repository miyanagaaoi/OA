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
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.626Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
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
