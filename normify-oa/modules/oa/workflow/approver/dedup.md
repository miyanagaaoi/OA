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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.763Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
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
