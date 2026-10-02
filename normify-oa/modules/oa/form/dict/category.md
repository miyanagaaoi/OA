---
uid: 4a4f90ca
id: oa.form.dict.category
parent: oa.form.dict
state: planned
name: {zh: "事项类别字典", en: "Category Dictionary"}
description:
  zh: >
      事项类别 category：经营 business / 经济 economy / 行政 admin / 人力 hr / 投资 invest（Q10 新增）；配置项可增删，五个类别统一归属财务部（财务管理），不细分。类别不再决定归口部门，仅作分类标签与统计维度（V0.4：经营 code 由 operate 改为 business）。
      
  en: >
      Matter category `category`: business / economy / admin / hr / invest (added by Q10); configurable and extendable, with all five categories owned centrally by the finance department. Category never determines ownership - it is only a label and reporting dimension (V0.4: the code for 经营 changed from operate to business).
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.658Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
source:
  - path: "doc/forms.md"
    line: 180
    end_line: 192
  - path: "doc/forms.md"
    line: 298
    end_line: 298
  - path: "doc/forms.md"
    line: 314
    end_line: 317
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/dicts/category/items"
    description:
      zh: >
          事项类别可选值列表。
          
      en: >
          Lists selectable category values.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/dicts/category/{code}/owner-dept"
    description:
      zh: >
          返回类别的归口部门（恒为财务部）。
          
      en: >
          Returns the category's central owner (always finance).
          
---
