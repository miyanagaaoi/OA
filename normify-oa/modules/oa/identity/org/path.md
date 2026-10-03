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
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.233Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
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
