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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.699Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 514
    end_line: 516
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
