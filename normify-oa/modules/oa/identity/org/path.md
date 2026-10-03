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
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.299Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/data-model.md"
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/data-model.md` → `CREATE TABLE sys_org`（§2. 身份与组织）
- `doc/prd-0.1.md` → `REQ-ORG-001`（§5.1 组织架构模型）
