---
uid: 1e85d872
id: oa.workflow.designer.validation
parent: oa.workflow.designer
state: planned
name: {zh: "发布前校验", en: "Pre-Publish Validation"}
description:
  zh: >
      发布前校验：主干节点连续性、审批人规则必填、会签阈值合法、超时 ≥24h、跳过条件字段存在、规则不致候选人恒空、决议模式完整；**驳回后的去向不参与节点级校验**——一期固定为「回到发起人」，不做节点级配置（节点级驳回去向属二期，见 doc/prd-0.1.md §5.4）；任一项不通过即阻止发布并给出具体原因。
      
  en: >
      Pre-publish validation: trunk node continuity, mandatory approver rule, valid countersign threshold, timeout ≥24h, skip-condition field existence, no rule that always yields an empty candidate set and complete decision modes; the post-rejection destination is NOT part of per-node validation — phase 1 fixes it to "back to the initiator" with no per-node configuration (per-node reject routing belongs to phase 2; see doc/prd-0.1.md §5.4); any failure blocks publishing with a concrete reason.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.600Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow-designs/{template_id}/pre-publish-check"
    description:
      zh: >
          执行发布前校验并返回逐条结论。
          
      en: >
          Runs the pre-publish checks and returns per-rule results.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow-designs/{template_id}/pre-publish-check/latest"
    description:
      zh: >
          读取最近一次校验结果。
          
      en: >
          Reads the latest validation result.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow-designs/check-rules"
    description:
      zh: >
          列出发布前校验规则清单。
          
      en: >
          Lists the pre-publish validation rules.
          
deps:
  - kind: call
    to: oa.workflow.definition.node-behavior.decision
    from_api: "POST /api/v1/flow-designs/{template_id}/pre-publish-check"
    to_api: "GET /api/v1/flow-nodes/{node_id}/decision"
    label: {zh: "校验决议配置", en: "Validate decision config"}
  - kind: call
    to: oa.workflow.definition.node-behavior.policy
    from_api: "POST /api/v1/flow-designs/{template_id}/pre-publish-check"
    to_api: "GET /api/v1/flow-nodes/{node_id}/policy"
    label: {zh: "校验超时与签名", en: "Validate timeout & signature"}
  - kind: call
    to: oa.workflow.definition.approver-rule
    from_api: "POST /api/v1/flow-designs/{template_id}/pre-publish-check"
    to_api: "GET /api/v1/flow-nodes/{node_id}/approver-rule"
    label: {zh: "校验解析规则", en: "Validate approver rule"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-008`（§6.4 流程引擎核心能力）
