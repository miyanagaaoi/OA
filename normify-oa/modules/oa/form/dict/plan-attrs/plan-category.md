---
uid: 6351c55b
id: oa.form.dict.plan-attrs.plan-category
parent: oa.form.dict.plan-attrs
state: planned
name: {zh: "计划类别字典", en: "Plan Category Dictionary"}
description:
  zh: >
      计划类别取值：in_plan 计划内（默认勾选）/ out_plan 计划外；二期「计划管理」上线后才具业务含义，一期不实现任何计划编制、比对或超计划拦截。
  en: >
      Plan category values: in_plan (checked by default) and out_plan; they gain meaning only when phase-two plan management ships, with no plan drafting, comparison or over-plan blocking in phase one.
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:10:00Z"
fingerprint: pending
source:
  - path: "doc/forms.md"
    line: 243
    end_line: 250
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/dicts/plan-category/items"
    description:
      zh: >
          计划类别可选值（含默认勾选）。
      en: >
          Lists plan category options including the default.
---
