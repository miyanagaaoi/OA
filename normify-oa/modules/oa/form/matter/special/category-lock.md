---
uid: 39013d48
id: oa.form.matter.special.category-lock
parent: oa.form.matter.special
state: planned
name: {zh: "事项类别不可改判", en: "Category Lock"}
description:
  zh: >
      事项类别由发起人选择后，任何审批节点都不能修改；分类错误的唯一处理路径是驳回给发起人重新提交。类别取值来自后台数据字典配置项，不再决定归口部门，仅作分类标签与统计维度。
      
  en: >
      Once the initiator picks the category, no approval node may change it; the only remedy for a wrong classification is rejecting back to the initiator. Category values come from admin-configured dictionary items, no longer determine central ownership, and serve only as a label and reporting dimension.
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.705Z"
fingerprint: 7e6e1785f6cab514c996533289fd117858ebc22094e197ef5442d58574cba7a5
source:
  - path: "doc/forms.md"
    line: 80
    end_line: 80
  - path: "doc/forms.md"
    line: 91
    end_line: 91
  - path: "doc/prd-0.1.md"
    line: 259
    end_line: 259
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/matter/fields/category/lock"
    description:
      zh: >
          提交后锁定事项类别。
          
      en: >
          Locks the category after submission.
          
  - protocol: http
    method: GET
    path: "/api/v1/forms/matter/instances/{instance_id}/category-change-check"
    description:
      zh: >
          校验事项类别是否被越权修改。
          
      en: >
          Checks whether the category was changed without permission.
          
deps:
  - kind: call
    to: oa.form.dict.category
    from_api: "GET /api/v1/forms/matter/instances/{instance_id}/category-change-check"
    to_api: "GET /api/v1/forms/dicts/category/items"
    label: {zh: "类别取值来源", en: "Category option source"}
---
