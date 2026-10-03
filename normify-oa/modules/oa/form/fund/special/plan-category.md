---
uid: 4855b377
id: oa.form.fund.special.plan-category
parent: oa.form.fund.special
name: {zh: "计划类别（一期只存不用）", en: "Plan Category (Store Only)"}
description:
  zh: >
      计划类别 plan_category（checkbox、取值见 6.7：计划内/计划外、默认勾选计划内）；一期仅存储数据，不参与任何流程判断，打印稿按纸质实单以 ☑/☐ 呈现；二期计划管理上线后才具业务含义。
      
  en: >
      Plan category `plan_category` (checkbox per 6.7: in-plan/out-of-plan, defaults to in-plan); phase one stores the value without any flow logic and prints it as ☑/☐ per the paper form; it gains business meaning only when phase-two plan management ships.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.013Z"
fingerprint: fcf07a02d1e78c7b0431fef35adfd1940dba00be452872407e2af70620c1cbfd
source:
  - path: "doc/forms.md"
  - path: "doc/forms.md"
  - path: "doc/forms.md"
  - path: "oa-server/src/main/java/com/oa/form/fund/FundFormRules.java"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/fund/fields/plan-category"
    description:
      zh: >
          读取计划类别字段与默认勾选。
          
      en: >
          Reads the plan category field and its default.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/fund/fields/plan-category/assert-storage-only"
    description:
      zh: >
          校验该字段未被流程条件/数据域/超时规则引用。
          
      en: >
          Asserts the field is unused by flow conditions, data scopes or timeout rules.
          
deps:
  - kind: reference
    to: oa.form.dict.plan-attrs
    from_api: "GET /api/v1/forms/fund/fields/plan-category"
    label: {zh: "字典取值来源", en: "Option source"}
---

## 证据锚点
- `doc/forms.md` → `### 6.7 计划类别 `plan_category`（**非字典项**：布尔 checkbox，一期仅存储）`（§6.7 计划类别 `plan_category`）
