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
      
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:01:04.995Z"
fingerprint: pending
source:
  - path: "doc/prd-0.1.md"
    line: 135
    end_line: 158
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
