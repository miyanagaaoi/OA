---
uid: "33800628"
id: oa.workflow.approver.dedup
parent: oa.workflow.approver
state: planned
name: {zh: "候选人去重与合并策略", en: "Candidate Dedup & Merge Policy"}
description:
  zh: >
      同一人在同一节点出现多次时自动去重（一人多岗、多组织挂职）；同一人同时是多个串行节点的审批人时默认逐节点分别审批，不做连续节点自动合并，如需合并由流程设计器显式配置。
      
  en: >
      Automatically de-duplicates a person appearing multiple times inside one node (multi-post or multi-org assignments); when the same person approves several sequential nodes the default is one approval per node with no automatic merging unless the designer explicitly configures it.
      
revision: e3b34a3c59417096ade647fab4261b06f6b605e2
updated_at: "2026-10-03T02:30:28.801Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 231
    end_line: 232
apis:
  - protocol: http
    method: POST
    path: "/api/v1/approver-rules/dedup"
    description:
      zh: >
          同一人在同一节点出现多次时自动去重。
          
      en: >
          De-duplicates a person appearing multiple times inside one node.
          
  - protocol: http
    method: GET
    path: "/api/v1/approver-rules/merge-policy"
    description:
      zh: >
          返回连续节点是否合并（未配置即不合并）。
          
      en: >
          Returns whether consecutive nodes merge (off unless the designer configures it).
          
deps:
  - kind: reference
    to: oa.identity.user
    from_api: "POST /api/v1/approver-rules/dedup"
    label: {zh: "以用户 id 去重", en: "Dedup key is user id"}
  - kind: reference
    to: oa.workflow.definition.node-schema
    from_api: "POST /api/v1/approver-rules/dedup"
    to_api: "GET /api/v1/flow-templates/{template_id}/nodes"
    label: {zh: "串行节点判定", en: "Sequential node check"}
---
