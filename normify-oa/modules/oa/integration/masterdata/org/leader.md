---
uid: 30af54b0
id: oa.integration.masterdata.org.leader
parent: oa.integration.masterdata.org
name: {zh: "节点负责人", en: "Org Node Leaders"}
description:
  zh: >
      查询某组织节点绑定的负责人及其类型（正职/副职）与排序，供下游系统路由单据时使用。
      
  en: >
      Leaders bound to an org node with their type (primary/deputy) and sort order, used by downstream systems to route documents.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.315Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
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
