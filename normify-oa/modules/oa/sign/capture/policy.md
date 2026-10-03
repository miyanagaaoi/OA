---
uid: 11dae6d4
id: oa.sign.capture.policy
parent: oa.sign.capture
state: planned
name: {zh: "节点签名要求判定", en: "Node Signature Policy"}
description:
  zh: >
      按流程节点配置判定本次审批的签名要求：强制签名 / 可选签名 / 不签名；默认集团分管领导与集团董事长节点强制签名，其余可选；强制节点未签名不得通过，并提供设计器配置接口（随模板发布生效）。
      
  en: >
      Resolves the signature requirement of a node (mandatory / optional / none). Group leaders and the chairman are mandatory by default; a mandatory node cannot be approved unsigned. Includes the designer configuration endpoint.
      
revision: e3b34a3c59417096ade647fab4261b06f6b605e2
updated_at: "2026-10-03T02:30:28.791Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 367
    end_line: 367
  - path: "doc/prd-0.1.md"
    line: 457
    end_line: 457
  - path: "doc/prd-0.1.md"
    line: 556
    end_line: 556
apis:
  - protocol: http
    method: POST
    path: "/api/v1/sign/capture/requirements/resolve"
    description:
      zh: >
          解析指定节点或任务的签名要求（强制/可选/不签名）。
          
      en: >
          Resolves the signature requirement (mandatory/optional/none) of a node or task.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow/nodes/{node_id}/sign-policy"
    description:
      zh: >
          读取节点签名策略。
          
      en: >
          Reads the signature policy of a flow node.
          
  - protocol: http
    method: PUT
    path: "/api/v1/flow/nodes/{node_id}/sign-policy"
    description:
      zh: >
          配置节点签名策略（强制/可选/不签名）。
          
      en: >
          Configures the node signature policy (mandatory/optional/none).
          
deps:
  - kind: reference
    to: oa.workflow.definition
    label: {zh: "节点定义 sign_policy 字段", en: "sign_policy on node definition"}
  - kind: reference
    to: oa.workflow.designer
    label: {zh: "流程设计器配置入口", en: "Process designer entry"}
---
