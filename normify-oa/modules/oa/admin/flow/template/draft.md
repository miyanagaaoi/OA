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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.624Z"
fingerprint: 4421092e639ef98e9cd4aac53b5975054268b604256d140e3544a8a3c2ba5551
source:
  - path: "doc/prd-0.1.md"
    line: 436
    end_line: 436
  - path: "doc/data-model.md"
    line: 278
    end_line: 295
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
