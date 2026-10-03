---
uid: 2e5847cd
id: oa.form.matter.fields.cost
parent: oa.form.matter.fields
name: {zh: "费用字段组", en: "Cost Field Group"}
description:
  zh: >
      是否涉及费用 involve_cost（boolean、必填、默认否）、涉及金额 amount（amount、条件必填、金额规则见 1.5）、费用承担主体 cost_bearer（org、条件必填、默认发起人所属公司、限本公司及以下节点）；involve_cost 同时决定财务部复核节点是否跳过。
      
  en: >
      Cost involved `involve_cost` (boolean, required, default no), amount `amount` (amount, conditionally required, see rule 1.5) and cost bearer `cost_bearer` (org, conditionally required, defaults to the initiator's company, limited to that company and below); involve_cost also decides whether the finance review node is skipped.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.442Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/matter/field-groups/cost"
    description:
      zh: >
          事项单费用字段组定义。
          
      en: >
          Cost field group definition for matter forms.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/matter/fields/involve-cost/linkage"
    description:
      zh: >
          求值涉及费用字段的显示与必填联动（路由判定见专用规则）。
          
      en: >
          Evaluates visibility and requiredness linkage for the cost field (the routing decision lives in the dedicated rules).
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/matter/fields/cost-bearer/default"
    description:
      zh: >
          取发起人所属公司作为费用承担主体默认值。
          
      en: >
          Returns the initiator's company as the default cost bearer.
          
deps:
  - kind: reference
    to: oa.identity.org
    from_api: "GET /api/v1/forms/matter/fields/cost-bearer/default"
    label: {zh: "所属公司默认值", en: "Default company value"}
  - kind: call
    to: oa.form.matter.special.cost-branch
    from_api: "POST /api/v1/forms/matter/fields/involve-cost/linkage"
    to_api: "POST /api/v1/forms/matter/fields/involve-cost/evaluate"
    label: {zh: "路由判定归属", en: "Routing decision owner"}
---

## 证据锚点
- `doc/forms.md` → `## 2. 事项审批单（`form_type = matter`）`（§2. 事项审批单）
