---
uid: 4d407179
id: oa.admin.flow.node.decision
parent: oa.admin.flow.node
state: planned
name: {zh: "决议模式与阈值", en: "Decision Mode & Threshold"}
description:
  zh: >
      逐节点配置或签/会签/依次审批，并通过阈值支持百分比与绝对人数两种写法（二者同时存在时以绝对人数优先），并校验阈值合法性。
      
  en: >
      Configures any-sign, all-sign or sequential approval per node together with a pass threshold expressed as a percentage or an absolute headcount, with the absolute form taking precedence when both are present.
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.621Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 218
    end_line: 232
  - path: "doc/prd-0.1.md"
    line: 548
    end_line: 549
apis:
  - protocol: http
    method: PUT
    path: "/api/v1/admin/flow-nodes/{node_id}/decision"
    description:
      zh: >
          设置节点的或签/会签/依次与通过阈值。
          
      en: >
          Set decision mode and pass threshold for a node.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/flow-nodes/{node_id}/decision/validate"
    description:
      zh: >
          校验阈值写法（百分比或绝对人数）。
          
      en: >
          Validate the threshold form (percent or headcount).
          
deps:
  - kind: call
    to: oa.workflow.runtime
    from_api: "PUT /api/v1/admin/flow-nodes/{node_id}/decision"
    label: {zh: "决议模式由流程引擎执行", en: "Decision mode in engine"}
  - kind: dataflow
    to: oa.workflow.definition.node-schema
    to_api: "mysql:flow_node"
    label: {zh: "写入流程节点定义", en: "Write flow node schema"}
---
