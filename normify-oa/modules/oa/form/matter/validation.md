---
uid: 331c1ba3
id: oa.form.matter.validation
parent: oa.form.matter
name: {zh: "事项单校验规则", en: "Matter Form Validation"}
description:
  zh: >
      事项单专属校验：标题 ≤60、事项描述 ≥10 且 ≤2000、involve_cost=是 时 amount/cost_bearer 条件必填、expect_date 不早于今天、cc_users ≤20 且去重、附件格式与大小；全部在服务端执行。
      
  en: >
      Matter-specific validation: title ≤60, description ≥10 and ≤2000, amount/cost_bearer required when involve_cost=yes, expect_date not earlier than today, cc_users ≤20 and de-duplicated, attachment format and size; all server-side.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.021Z"
fingerprint: db3ec03bd48cba01f4168dbba0c57475ff51dce3e9a1e32a3e53547c3db004d8
source:
  - path: "doc/forms.md"
  - path: "oa-server/src/main/java/com/oa/form/matter/MatterFormRules.java"
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

## 证据锚点
- `doc/forms.md` → `## 2. 事项审批单（`form_type = matter`）`（§2. 事项审批单）
