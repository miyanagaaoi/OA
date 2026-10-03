---
uid: 0369f88d
id: oa.identity.org.path
parent: oa.identity.org
name: {zh: "路径与层级重算", en: "Path & Depth Rebuild"}
description:
  zh: >
      组织节点移动后的祖先路径（/1/12/135/）、层级与排序级联重算，并提供祖先/后代查询；路径唯一键保证同一节点不被重复挂载。
      
  en: >
      Rebuilds the materialised ancestor path (/1/12/135/), depth and sort order after a node move, and answers ancestor/descendant queries; the path unique key prevents duplicate mounting.
      
revision: e3b34a3c59417096ade647fab4261b06f6b605e2
updated_at: "2026-10-03T02:30:28.724Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/data-model.md"
    line: 34
    end_line: 54
  - path: "doc/prd-0.1.md"
    line: 133
    end_line: 137
apis:
  - protocol: http
    method: POST
    path: "/api/v1/identity/orgs/{id}/move"
    description:
      zh: >
          移动节点并级联重算路径。
          
      en: >
          Moves a node and rebuilds descendant paths.
          
  - protocol: http
    method: GET
    path: "/api/v1/identity/orgs/{id}/ancestors"
    description:
      zh: >
          查询祖先节点链。
          
      en: >
          Lists ancestor nodes.
          
  - protocol: http
    method: GET
    path: "/api/v1/identity/orgs/{id}/descendants"
    description:
      zh: >
          查询后代节点。
          
      en: >
          Lists descendant nodes.
          
  - protocol: http
    method: GET
    path: "/api/v1/identity/orgs/{id}/path"
    description:
      zh: >
          读取节点路径与层级。
          
      en: >
          Reads the node path and depth.
          
deps:
  - kind: call
    to: oa.identity.org.node
    from_api: "POST /api/v1/identity/orgs/{id}/move"
    to_api: "PUT /api/v1/identity/orgs/{id}"
    label: {zh: "移动后回写路径与层级", en: "Write back path/depth"}
---
