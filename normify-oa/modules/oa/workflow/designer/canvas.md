---
uid: 1bda30bc
id: oa.workflow.designer.canvas
parent: oa.workflow.designer
name: {zh: "画布编排与节点顺序", en: "Canvas Orchestration & Node Order"}
description:
  zh: >
      图形化流程设计器的画布层：读取模板节点图、节点增删与顺序调整、连线预览；编排结果写回 flow_node 定义，仅允许在草稿版本上编辑（REQ-FLOW-008）。
      
  en: >
      Canvas layer of the graphical designer: loads the template node graph, adds and removes nodes, reorders them and previews links; the result is written back to flow_node definitions and editing is allowed only on draft versions (REQ-FLOW-008).
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.366Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/flow-designs/{template_id}/graph"
    description:
      zh: >
          读取可编辑的节点图（节点、顺序与连线）。
          
      en: >
          Reads the editable node graph (nodes, order and links).
          
  - protocol: http
    method: PUT
    path: "/api/v1/flow-designs/{template_id}/graph"
    description:
      zh: >
          整体保存画布编排结果。
          
      en: >
          Saves the whole canvas orchestration.
          
  - protocol: http
    method: POST
    path: "/api/v1/flow-designs/{template_id}/nodes"
    description:
      zh: >
          在画布上新增节点。
          
      en: >
          Adds a node on the canvas.
          
  - protocol: http
    method: DELETE
    path: "/api/v1/flow-designs/{template_id}/nodes/{node_id}"
    description:
      zh: >
          从画布删除节点并重排后续 seq。
          
      en: >
          Deletes a node from the canvas and renumbers later seq values.
          
  - protocol: http
    method: PUT
    path: "/api/v1/flow-designs/{template_id}/nodes/order"
    description:
      zh: >
          调整节点顺序并重排 seq。
          
      en: >
          Reorders nodes and renumbers their seq values.
          
deps:
  - kind: call
    to: oa.workflow.definition.node-schema
    from_api: "POST /api/v1/flow-designs/{template_id}/nodes"
    to_api: "POST /api/v1/flow-templates/{template_id}/nodes"
    label: {zh: "落库节点定义", en: "Persist node definition"}
  - kind: call
    to: oa.workflow.definition.template.registry
    from_api: "GET /api/v1/flow-designs/{template_id}/graph"
    to_api: "GET /api/v1/flow-templates/{template_id}"
    label: {zh: "读模板与表单绑定", en: "Read template & form binding"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-008`（§6.4 流程引擎核心能力）
