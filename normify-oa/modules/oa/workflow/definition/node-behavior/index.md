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
      
revision: c22d447e6e63ccb0edfd9624026f21e8d1413077
updated_at: "2026-10-03T02:20:07.737Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
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
