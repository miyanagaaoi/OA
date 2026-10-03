---
uid: 334fab86
id: oa.form.matter.special.cost-branch
parent: oa.form.matter.special
state: planned
name: {zh: "涉及费用分支与节点跳过", en: "Cost Branch & Node Skip"}
description:
  zh: >
      involve_cost 是一期全局唯一路由判据：=否 时跳过节点②财务部复核，该节点状态记为「已跳过」且不产生待办，但单据归口部门仍记为财务部用于统计与审计；轨迹记录「本单不涉及费用，财务节点已跳过」。金额不参与路由。
      
  en: >
      involve_cost is the only routing criterion in phase one: when no, node ② finance review is skipped with status skipped and no todo, yet the document's central-ownership field still records the finance department for reporting and audit; the trail records that the finance node was skipped. Amounts never drive routing.
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.715Z"
fingerprint: a590145bc5717823c1c716f7cd29b05065b1aa8bf16dddb19dfc3b30d5207ee6
source:
  - path: "doc/forms.md"
    line: 82
    end_line: 82
  - path: "doc/forms.md"
    line: 92
    end_line: 92
  - path: "doc/forms.md"
    line: 349
    end_line: 349
  - path: "doc/prd-0.1.md"
    line: 264
    end_line: 264
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/matter/fields/involve-cost/evaluate"
    description:
      zh: >
          求值是否跳过财务部复核节点。
          
      en: >
          Evaluates whether the finance review node is skipped.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/matter/instances/{instance_id}/branch"
    description:
      zh: >
          读取事项单分支判定与归口部门记录。
          
      en: >
          Reads the matter branch decision and central-ownership record.
          
deps:
  - kind: call
    to: oa.workflow.routing
    from_api: "POST /api/v1/forms/matter/fields/involve-cost/evaluate"
    label: {zh: "路由判据", en: "Routing criterion"}
---
