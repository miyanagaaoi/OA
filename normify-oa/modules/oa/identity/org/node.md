---
uid: 023fdb78
id: oa.identity.org.node
parent: oa.identity.org
state: planned
name: {zh: "组织节点维护", en: "Organization Node CRUD"}
description:
  zh: >
      维护集团-公司-部门-科室四级组织节点：名称、类型（group/company/dept/section）、上级、排序与软删除；写入时同步 path 与 depth，是人员归属、负责人绑定与数据域判定的基础数据。
  en: >
      Maintains the four-level org nodes (group/company/department/section): name, type, parent, sort and soft delete; path and depth are written here and underpin user affiliation, leader binding and data scope.
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:10:00Z"
fingerprint: pending
source:
  - path: "doc/data-model.md"
    line: 34
    end_line: 54
  - path: "doc/prd-0.1.md"
    line: 133
    end_line: 137
apis:
  - protocol: http
    method: GET
    path: "/api/v1/identity/orgs/tree"
    description:
      zh: >
          查询组织树。
      en: >
          Reads the organization tree.
  - protocol: http
    method: POST
    path: "/api/v1/identity/orgs"
    description:
      zh: >
          新建组织节点。
      en: >
          Creates an org node.
  - protocol: http
    method: PUT
    path: "/api/v1/identity/orgs/{id}"
    description:
      zh: >
          修改节点名称/类型/排序/状态。
      en: >
          Updates name, type, sort or status of a node.
  - protocol: http
    method: DELETE
    path: "/api/v1/identity/orgs/{id}"
    description:
      zh: >
          软删除组织节点。
      en: >
          Soft-deletes an org node.
  - protocol: mysql
    path: "sys_org"
    description:
      zh: >
          组织架构表。
      en: >
          Organization table.
---
