---
uid: 07b4284d
id: oa.workflow.definition.node-schema
parent: oa.workflow.definition
state: planned
name: {zh: "节点定义与节点类型", en: "Node Definitions & Node Types"}
description:
  zh: >
      flow_node 节点定义本体：节点序号 seq（与 PRD 6.3 的①②③对应）、node_code、显示名与节点类型 node_type（approve 审批 / condition 条件（二期预留）/ cc 抄送 / archive 归档登记）；维护模板内 seq 唯一与节点增删改。
      
  en: >
      The flow_node definition itself: sequence seq mapped to PRD 6.3's ①-⑦, node_code, display name and node_type (approve / condition reserved for phase 2 / cc / archive), keeping seq unique within a template and supporting node add, update and delete.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.423Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/data-model.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: mysql
    path: "flow_node"
    description:
      zh: >
          流程节点定义表（seq、节点类型、行为配置列）。
          
      en: >
          Flow node definition table (seq, node type, behavior columns).
          
  - protocol: http
    method: GET
    path: "/api/v1/flow-templates/{template_id}/nodes"
    description:
      zh: >
          按模板读取节点定义列表（按 seq 排序）。
          
      en: >
          Lists node definitions of a template ordered by seq.
          
  - protocol: http
    method: POST
    path: "/api/v1/flow-templates/{template_id}/nodes"
    description:
      zh: >
          在草稿模板上新增节点并分配 seq。
          
      en: >
          Adds a node to a draft template and assigns its seq.
          
  - protocol: http
    method: PUT
    path: "/api/v1/flow-nodes/{node_id}"
    description:
      zh: >
          更新节点名称、编码与节点类型。
          
      en: >
          Updates node name, code and node type.
          
  - protocol: http
    method: DELETE
    path: "/api/v1/flow-nodes/{node_id}"
    description:
      zh: >
          删除草稿模板中的节点。
          
      en: >
          Deletes a node from a draft template.
          
deps:
  - kind: call
    to: oa.workflow.definition.template.registry
    from_api: "POST /api/v1/flow-templates/{template_id}/nodes"
    to_api: "GET /api/v1/flow-templates/{template_id}"
    label: {zh: "校验模板为草稿态", en: "Verify template is a draft"}
---

## 证据锚点
- `doc/data-model.md` → `CREATE TABLE flow_node`（§4. 流程定义）
- `doc/prd-0.1.md` → `REQ-FLOW-008`（§6.4 流程引擎核心能力）
