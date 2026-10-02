---
uid: 1e85d872
id: oa.workflow.designer.validation
parent: oa.workflow.designer
state: planned
name: {zh: "发布前校验", en: "Pre-Publish Validation"}
description:
  zh: >
      发布前校验：主干节点连续性、审批人规则必填、会签阈值合法、会签须定义驳回即终止或回退、超时 ≥24h、跳过条件字段存在、规则不致候选人恒空、决议模式完整；任一项不通过即阻止发布并给出具体原因。
      
  en: >
      Pre-publish validation: trunk node continuity, mandatory approver rule, valid countersign threshold, explicit reject-terminate/return for countersign, timeout ≥24h, skip-condition field existence, no rule that always yields an empty candidate set and complete decision modes; any failure blocks publishing with a concrete reason.
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.810Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 229
    end_line: 230
  - path: "doc/prd-0.1.md"
    line: 351
    end_line: 352
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
