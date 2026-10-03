---
uid: 1e6f393f
id: oa.workflow.designer.node-editor
parent: oa.workflow.designer
state: planned
name: {zh: "节点属性面板", en: "Node Property Panel"}
description:
  zh: >
      设计器节点属性面板：选择审批人解析规则、配置决议模式与通过阈值、签名是否强制、超时时长、是否允许加签/跳转，统一透传到节点行为配置与规则声明（REQ-FLOW-008）。
      
  en: >
      Designer node property panel: choose the approver rule, configure decision mode and pass threshold, mandatory signature, timeout hours and the add-sign/jump switches, forwarded uniformly to the node behavior config and rule declaration (REQ-FLOW-008).
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.797Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 352
    end_line: 352
  - path: "doc/prd-0.1.md"
    line: 218
    end_line: 232
apis:
  - protocol: http
    method: GET
    path: "/api/v1/flow-designs/{template_id}/nodes/{node_id}/properties"
    description:
      zh: >
          读取节点属性面板所需的全部配置。
          
      en: >
          Reads all configuration needed by the node property panel.
          
  - protocol: http
    method: PUT
    path: "/api/v1/flow-designs/{template_id}/nodes/{node_id}/properties"
    description:
      zh: >
          保存节点属性面板改动并分发到各配置模块。
          
      en: >
          Saves property panel changes and dispatches them to the config modules.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow-designs/options"
    description:
      zh: >
          提供规则、决议模式、节点类型与签名策略候选项。
          
      en: >
          Provides option catalogs for rules, decision modes, node types and signature policies.
          
deps:
  - kind: call
    to: oa.workflow.definition.node-behavior.decision
    from_api: "PUT /api/v1/flow-designs/{template_id}/nodes/{node_id}/properties"
    to_api: "PUT /api/v1/flow-nodes/{node_id}/decision"
    label: {zh: "写入决议模式", en: "Write decision mode"}
  - kind: call
    to: oa.workflow.definition.node-behavior.policy
    from_api: "PUT /api/v1/flow-designs/{template_id}/nodes/{node_id}/properties"
    to_api: "PUT /api/v1/flow-nodes/{node_id}/policy"
    label: {zh: "写入签名与超时", en: "Write signature & timeout"}
  - kind: call
    to: oa.workflow.definition.approver-rule
    from_api: "PUT /api/v1/flow-designs/{template_id}/nodes/{node_id}/properties"
    to_api: "PUT /api/v1/flow-nodes/{node_id}/approver-rule"
    label: {zh: "写入解析规则", en: "Write approver rule"}
---
