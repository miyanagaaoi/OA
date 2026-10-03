---
uid: 4a4f90ca
id: oa.form.dict.category
parent: oa.form.dict
name: {zh: "事项类别字典", en: "Category Dictionary"}
description:
  zh: >
      事项类别 category：经营 business / 经济 economy / 行政 admin / 人力 hr / 投资 invest（Q10 新增）；配置项可增删，五个类别统一归属财务部（财务管理），不细分。类别不再决定归口部门，仅作分类标签与统计维度（V0.4：经营 code 由 operate 改为 business）。
      
  en: >
      Matter category `category`: business / economy / admin / hr / invest (added by Q10); configurable and extendable, with all five categories owned centrally by the finance department. Category never determines ownership - it is only a label and reporting dimension (V0.4: the code for 经营 changed from operate to business).
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.260Z"
fingerprint: 1266f6407e434b7ee473c37529842b35c2605a39bc4ad8881f6bc19d40d4caf1
source:
  - path: "doc/forms.md"
  - path: "doc/forms.md"
  - path: "doc/forms.md"
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

## 证据锚点
- `doc/forms.md` → `### 6.1 事项类别（字段 code `category` · 字典类型 `matter_category`）`（§6.1 事项类别）
