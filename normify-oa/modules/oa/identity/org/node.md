---
uid: 023fdb78
id: oa.identity.org.node
parent: oa.identity.org
name: {zh: "组织节点维护", en: "Organization Node CRUD"}
description:
  zh: >
      维护集团-公司-部门-科室四级组织节点：名称、类型（group/company/dept/section）、上级、排序与软删除；写入时同步 path 与 depth，是人员归属、负责人绑定与数据域判定的基础数据。
      
  en: >
      Maintains the four-level org nodes (group/company/department/section): name, type, parent, sort and soft delete; path and depth are written here and underpin user affiliation, leader binding and data scope.
      
revision: 44fc7aba1c7e884ffa3553faf31bfce974b388a9
updated_at: "2026-10-03T04:12:20.726Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/data-model.md"
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/data-model.md` → `CREATE TABLE sys_org`（§2. 身份与组织）
- `doc/prd-0.1.md` → `REQ-ORG-001`（§5.1 组织架构模型）
