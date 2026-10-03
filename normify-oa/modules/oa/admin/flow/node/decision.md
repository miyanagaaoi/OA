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
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.421Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-002`（§6.4 流程引擎核心能力）
