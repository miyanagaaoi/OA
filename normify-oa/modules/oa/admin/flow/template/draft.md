---
uid: 4897a70b
id: oa.admin.flow.template.draft
parent: oa.admin.flow.template
state: planned
name: {zh: "模板草稿编辑", en: "Template Draft Editing"}
description:
  zh: >
      新建与编辑流程模板草稿（含节点清单与顺序），并把编排交给图形化设计器；未发布的草稿可直接删除。
      
  en: >
      Creates and edits flow template drafts, including node list and ordering, and hands the layout to the graphical designer; unpublished drafts can be deleted outright.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.125Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/flow-templates"
    description:
      zh: >
          查询流程模板列表（含版本与状态）。
          
      en: >
          List flow templates with version and status.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/flow-templates"
    description:
      zh: >
          新建流程模板草稿。
          
      en: >
          Create a flow template draft.
          
  - protocol: http
    method: PUT
    path: "/api/v1/admin/flow-templates/{template_id}"
    description:
      zh: >
          编辑模板草稿（节点、顺序、表单 Schema）。
          
      en: >
          Edit the draft (nodes, order, form schema).
          
  - protocol: http
    method: DELETE
    path: "/api/v1/admin/flow-templates/{template_id}"
    description:
      zh: >
          删除未发布的模板草稿。
          
      en: >
          Delete an unpublished template draft.
          
deps:
  - kind: call
    to: oa.workflow.definition
    from_api: "POST /api/v1/admin/flow-templates"
    label: {zh: "模板定义由流程域执行", en: "Template definition in flow"}
  - kind: call
    to: oa.workflow.designer
    label: {zh: "图形化编排", en: "Graphical orchestration"}
  - kind: dataflow
    to: oa.workflow.definition.template.registry
    to_api: "mysql:flow_template"
    label: {zh: "发布流程模板版本", en: "Publish template version"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-002`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE flow_template`（§4. 流程定义）
