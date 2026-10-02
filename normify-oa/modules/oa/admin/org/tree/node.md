---
uid: 1a7963be
id: oa.admin.org.tree.node
parent: oa.admin.org.tree
state: planned
name: {zh: "组织节点增删改", en: "Org Node CRUD"}
description:
  zh: >
      新建、修改、删除组织节点，维护节点类型（集团/公司/部门/科室）、上级、排序与物化路径；存在在途单据或下属人员的节点拒绝删除，只允许停用。
      
  en: >
      Create, update and delete org nodes, maintaining node type (group/company/department/section), parent, sort order and materialised path; a node with in-flight documents or members cannot be deleted, only disabled.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.608Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
source:
  - path: "doc/prd-0.1.md"
    line: 435
    end_line: 435
  - path: "doc/data-model.md"
    line: 34
    end_line: 54
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/orgs"
    description:
      zh: >
          查询组织树（可选包含停用节点）。
          
      en: >
          List the org tree, optionally including disabled nodes.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/orgs"
    description:
      zh: >
          新建组织节点并回填物化路径与层级。
          
      en: >
          Create an org node and backfill its path and depth.
          
  - protocol: http
    method: PUT
    path: "/api/v1/admin/orgs/{org_id}"
    description:
      zh: >
          修改节点名称、类型、上级与排序。
          
      en: >
          Update node name, type, parent and sort order.
          
  - protocol: http
    method: DELETE
    path: "/api/v1/admin/orgs/{org_id}"
    description:
      zh: >
          删除空节点；有在途单据或下属人员时拒绝。
          
      en: >
          Delete an empty node; rejected when it has in-flight documents or members.
          
deps:
  - kind: call
    to: oa.identity.org
    from_api: "POST /api/v1/admin/orgs"
    label: {zh: "写入组织架构权威数据", en: "Write authoritative org tree"}
  - kind: dataflow
    to: oa.identity.org.node
    to_api: "mysql:sys_org"
    label: {zh: "写入组织架构表", en: "Write org structure table"}
---
