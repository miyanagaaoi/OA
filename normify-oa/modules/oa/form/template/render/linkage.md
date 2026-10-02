---
uid: 0f3933ac
id: oa.form.template.render.linkage
parent: oa.form.template.render
state: planned
name: {zh: "字段联动求值", en: "Field Linkage Evaluation"}
description:
  zh: >
      联动规则求值：字段的显示、必填与取值依赖其他字段，如 involve_cost → amount/cost_bearer、contract_type → contract_type_other、seal_type → cert_name/seal_count、return_status → return_date、is_framework → period_end。
      
  en: >
      Evaluates linkage rules where visibility, requiredness and value depend on other fields, e.g. involve_cost → amount/cost_bearer, contract_type → contract_type_other, seal_type → cert_name/seal_count, return_status → return_date, is_framework → period_end.
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.696Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
    line: 23
    end_line: 23
  - path: "doc/forms.md"
    line: 82
    end_line: 84
  - path: "doc/forms.md"
    line: 132
    end_line: 133
  - path: "doc/forms.md"
    line: 159
    end_line: 164
  - path: "doc/forms.md"
    line: 139
    end_line: 139
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/render/{form_type}/linkage"
    description:
      zh: >
          求值联动结果（显示/必填/可写/取值）。
          
      en: >
          Evaluates linkage results (visibility, requiredness, writability, derived value).
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/templates/{form_type}/schema/linkages"
    description:
      zh: >
          读取模板声明的联动规则。
          
      en: >
          Reads the linkage rules declared by a template.
          
---
