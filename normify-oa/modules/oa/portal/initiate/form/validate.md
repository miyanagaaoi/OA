---
uid: 1afb9c3c
id: oa.portal.initiate.form.validate
parent: oa.portal.initiate.form
state: planned
name: {zh: "表单校验与定位", en: "Form Validation & Focus"}
description:
  zh: >
      提交前的字段校验：前端即时校验仅作体验，必填 / 长度 / 金额 / 日期一律由服务端二次校验；提交前滚动到第一个校验失败字段；错误态为语义红边框 + 下方 12px 红字说明；金额为 0 或空时禁止提交（资金审批单金额必填）。
      
  en: >
      Pre-submit field validation: client-side checks are convenience only, while required, length, amount and date rules are always re-validated on the server; on submit the page scrolls to the first failing field; the error state is a semantic-red border with a 12px red message below; an amount of zero or blank blocks submission.
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.784Z"
fingerprint: f4473a365d6ffe090018b7c40180e7df64e246602e38a0c608b6eb114b853dce
source:
  - path: "doc/forms.md"
    line: 398
    end_line: 398
  - path: "DESIGN.md"
    line: 847
    end_line: 847
  - path: "DESIGN.md"
    line: 849
    end_line: 849
apis:
  - protocol: http
    method: POST
    path: "/api/v1/portal/initiate/validate"
    description:
      zh: >
          提交前服务端二次校验，返回首个失败字段。
          
      en: >
          Server-side re-validation before submit, returning the first failing field.
          
  - protocol: http
    method: GET
    path: "/api/v1/portal/initiate/validate/required-rules"
    description:
      zh: >
          按单据类型下发的必填与金额规则。
          
      en: >
          Required-field and amount rules served per document type.
          
deps:
  - kind: call
    to: oa.form.template
    from_api: "POST /api/v1/portal/initiate/validate"
    label: {zh: "按模板字段规则校验", en: "Validate via template rules"}
  - kind: call
    to: oa.form.fund
    from_api: "GET /api/v1/portal/initiate/validate/required-rules"
    label: {zh: "资金单金额必填与为零禁止提交", en: "Fund amount required, non-zero"}
---
