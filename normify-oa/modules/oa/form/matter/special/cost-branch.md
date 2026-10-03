---
uid: 334fab86
id: oa.form.matter.special.cost-branch
parent: oa.form.matter.special
name: {zh: "涉及费用分支与节点跳过", en: "Cost Branch & Node Skip"}
description:
  zh: >
      involve_cost 是一期全局唯一路由判据：=否 时跳过节点②财务部复核，该节点状态记为「已跳过」且不产生待办，但单据归口部门仍记为财务部用于统计与审计；轨迹记录「本单不涉及费用，财务节点已跳过」。金额不参与路由。
      
  en: >
      involve_cost is the only routing criterion in phase one: when no, node ② finance review is skipped with status skipped and no todo, yet the document's central-ownership field still records the finance department for reporting and audit; the trail records that the finance node was skipped. Amounts never drive routing.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.272Z"
fingerprint: 0cdd37e2608d44d9b994eb3494e555caf1e4fd4be4c2594c466fd048c3def3c0
source:
  - path: "doc/forms.md"
  - path: "doc/forms.md"
  - path: "doc/prd-0.1.md"
  - path: "oa-server/src/main/java/com/oa/form/matter/MatterFormRules.java"
  - path: "oa-server/src/main/java/com/oa/workflow/runtime/domain/FlowLinkage.java"
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

## 证据锚点
- `doc/forms.md` → `### 9.1 路由模型简化（Q10 的连带结论）`（§9.1 路由模型简化）
- `doc/prd-0.1.md` → `REQ-FLOW-025`（§6.3 主干审批链）
