---
uid: 4855b377
id: oa.form.fund.special.plan-category
parent: oa.form.fund.special
state: planned
name: {zh: "计划类别（一期只存不用）", en: "Plan Category (Store Only)"}
description:
  zh: >
      计划类别 plan_category（checkbox、取值见 6.7：计划内/计划外、默认勾选计划内）；一期仅存储数据，不参与任何流程判断，打印稿按纸质实单以 ☑/☐ 呈现；二期计划管理上线后才具业务含义。
      
  en: >
      Plan category `plan_category` (checkbox per 6.7: in-plan/out-of-plan, defaults to in-plan); phase one stores the value without any flow logic and prints it as ☑/☐ per the paper form; it gains business meaning only when phase-two plan management ships.
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.715Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
    line: 104
    end_line: 104
  - path: "doc/forms.md"
    line: 119
    end_line: 120
  - path: "doc/forms.md"
    line: 243
    end_line: 250
  - path: "doc/forms.md"
    line: 296
    end_line: 296
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
