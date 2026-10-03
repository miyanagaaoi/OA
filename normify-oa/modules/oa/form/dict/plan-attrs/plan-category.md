---
uid: 6351c55b
id: oa.form.dict.plan-attrs.plan-category
parent: oa.form.dict.plan-attrs
name: {zh: "计划类别字典", en: "Plan Category Dictionary"}
description:
  zh: >
      计划类别取值：in_plan 计划内（默认勾选）/ out_plan 计划外；二期「计划管理」上线后才具业务含义，一期不实现任何计划编制、比对或超计划拦截。
      
  en: >
      Plan category values: in_plan (checked by default) and out_plan; they gain meaning only when phase-two plan management ships, with no plan drafting, comparison or over-plan blocking in phase one.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.008Z"
fingerprint: 10c6effd215627f1c844a3acecef0995e0cae341763861c18553845cb71394d6
source:
  - path: "doc/forms.md"
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

## 证据锚点
- `doc/forms.md` → `### 6.7 计划类别 `plan_category`（**非字典项**：布尔 checkbox，一期仅存储）`（§6.7 计划类别 `plan_category`）
