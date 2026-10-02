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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.717Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
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
