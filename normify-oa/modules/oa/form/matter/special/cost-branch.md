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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.685Z"
fingerprint: 0af6565d61634a342e4b0a32164bffdd5bdb0d2843067bb40450b183df60b428
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
