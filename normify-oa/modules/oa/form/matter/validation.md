---
uid: 331c1ba3
id: oa.form.matter.validation
parent: oa.form.matter
state: planned
name: {zh: "事项单校验规则", en: "Matter Form Validation"}
description:
  zh: >
      事项单专属校验：标题 ≤60、事项描述 ≥10 且 ≤2000、involve_cost=是 时 amount/cost_bearer 条件必填、expect_date 不早于今天、cc_users ≤20 且去重、附件格式与大小；全部在服务端执行。
      
  en: >
      Matter-specific validation: title ≤60, description ≥10 and ≤2000, amount/cost_bearer required when involve_cost=yes, expect_date not earlier than today, cc_users ≤20 and de-duplicated, attachment format and size; all server-side.
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.287Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
    line: 79
    end_line: 87
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/matter/validate"
    description:
      zh: >
          事项单提交前整体校验。
          
      en: >
          Full pre-submit validation for matter forms.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/matter/validate/cost-conditional"
    description:
      zh: >
          涉及费用时的条件必填与金额校验。
          
      en: >
          Conditional-required and amount checks when cost is involved.
          
deps:
  - kind: call
    to: oa.form.template.validate
    from_api: "POST /api/v1/forms/matter/validate"
    label: {zh: "复用通用校验引擎", en: "Reuses shared validator"}
---
