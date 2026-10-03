---
uid: 045b6117
id: oa.identity.org.cascader
parent: oa.identity.org
name: {zh: "组织选择器与可见边界", en: "Org Cascader & Visibility"}
description:
  zh: >
      四级联级组织选择器与搜索定位（每级面板 240px、支持搜索）；无数据权限的组织节点不渲染（不可见优先于禁用），避免通过选择器探测组织架构。
      
  en: >
      Four-level org cascader with per-level search (240px panels); nodes outside the caller's data scope are not rendered at all (invisible rather than disabled) so the picker cannot be used to probe the org tree.
      
revision: 112ab0a1d46779714029044fc8e0b46627804f30
updated_at: "2026-10-03T01:51:21.598Z"
fingerprint: cd9efea98e4b8fd30bd3cc4099150a74c191c187c756c15dfd65a60a05dc8cbc
source:
  - path: "DESIGN.md"
    line: 845
    end_line: 912
  - path: "doc/prd-0.1.md"
    line: 159
    end_line: 168
apis:
  - protocol: http
    method: GET
    path: "/api/v1/identity/orgs/selector"
    description:
      zh: >
          返回权限边界内的联级组织选项。
          
      en: >
          Returns cascader options within the caller's scope.
          
  - protocol: http
    method: GET
    path: "/api/v1/identity/orgs/search"
    description:
      zh: >
          按名称搜索可见组织节点。
          
      en: >
          Searches visible org nodes by name.
          
deps:
  - kind: call
    to: oa.identity.org.node
    from_api: "GET /api/v1/identity/orgs/selector"
    to_api: "GET /api/v1/identity/orgs/tree"
    label: {zh: "读取组织节点树", en: "Read the org tree"}
  - kind: call
    to: oa.authz.scope.filter
    from_api: "GET /api/v1/identity/orgs/selector"
    to_api: "rpc:authz.scope.buildFilter"
    label: {zh: "按数据域过滤可见节点", en: "Filter by data scope"}
---
