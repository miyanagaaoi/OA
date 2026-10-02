---
uid: 0c7218c7
id: oa.workflow.definition.node-behavior
parent: oa.workflow.definition
state: planned
name: {zh: "节点行为配置", en: "Node Behavior Config"}
description:
  zh: >
      节点级行为配置三件套：决议模式与通过阈值、签名策略与超时/加签跳转开关、跳过条件解析；均在流程设计器中逐节点配置，并在发起时冻结进节点实例作为运行时依据。
      
  en: >
      Node-level behavior configuration in three parts: decision mode and pass threshold, signature policy with timeout/add-sign/jump switches, and skip-condition resolution; all configured per node in the designer and frozen into node instances at submission.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.752Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
source:
  - path: "doc/data-model.md"
    line: 306
    end_line: 316
  - path: "doc/prd-0.1.md"
    line: 344
    end_line: 352
deps:
  - kind: reference
    to: oa.workflow.definition.node-schema
    label: {zh: "配置落在节点表列", en: "Behavior lives on flow_node"}
---
