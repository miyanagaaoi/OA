---
uid: 1306c867
id: oa.integration.masterdata.org.detail
parent: oa.integration.masterdata.org
state: planned
name: {zh: "组织节点详情", en: "Org Node Detail"}
description:
  zh: >
      单个组织节点详情：类型、上级路径与启用/停用状态。
      
  en: >
      Single org node detail including type, parent path and enable/disable state.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.550Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/open/orgs/{id}"
    description:
      zh: >
          单个组织节点的只读详情。
          
      en: >
          Read-only detail of one org node.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `### 8.1 接口设计原则`（§8.1 接口设计原则）
