---
uid: 2e7c8c1a
id: oa.workflow.approver.precheck
parent: oa.workflow.approver
state: planned
name: {zh: "发起前拦截", en: "Pre-Submit Blockers"}
description:
  zh: >
      发起前拦截：候选人集合为空（如部门未设负责人、总经理离职未补）必须拒绝发起并提示「XX 节点无有效审批人，请联系管理员配置」，不允许静默跳过；同时拦截必填字段缺失与无权限组织节点（REQ-FLOW-012、AC-11）。
      
  en: >
      Blocks submission when any node's candidate set is empty (department without a leader, GM vacancy), reporting "no valid approver for node XX" instead of silently skipping, and also blocks missing required fields and unauthorized org nodes (REQ-FLOW-012, AC-11).
      
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:04:18.687Z"
fingerprint: pending
source:
  - path: "doc/prd-0.1.md"
    line: 230
    end_line: 230
  - path: "doc/prd-0.1.md"
    line: 356
    end_line: 356
  - path: "doc/data-model.md"
    line: 716
    end_line: 720
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow-instances/precheck"
    description:
      zh: >
          拦截发起并逐节点给出具体原因。
          
      en: >
          Blocks submission and reports the exact reason per node.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow-instances/precheck/rules"
    description:
      zh: >
          列出拦截规则（空候选人、必填缺失、无权限组织）。
          
      en: >
          Lists the blocking rules (empty candidates, missing fields, unauthorized org).
          
deps:
  - kind: call
    to: oa.workflow.approver.snapshot
    from_api: "POST /api/v1/flow-instances/precheck"
    to_api: "POST /api/v1/flow-instances/{instance_id}/approver-snapshot"
    label: {zh: "逐节点判空", en: "Check empty candidates"}
  - kind: call
    to: oa.form.template
    from_api: "POST /api/v1/flow-instances/precheck"
    label: {zh: "必填字段校验", en: "Required field check"}
  - kind: reference
    to: oa.identity.org
    from_api: "POST /api/v1/flow-instances/precheck"
    label: {zh: "无权限组织节点", en: "Unauthorized org node"}
---
