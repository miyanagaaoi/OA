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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.751Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 138
    end_line: 142
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
