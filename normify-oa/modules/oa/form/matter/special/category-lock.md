---
uid: 39013d48
id: oa.form.matter.special.category-lock
parent: oa.form.matter.special
name: {zh: "事项类别不可改判", en: "Category Lock"}
description:
  zh: >
      事项类别由发起人选择后，任何审批节点都不能修改；分类错误的唯一处理路径是驳回给发起人重新提交。类别取值来自后台数据字典配置项，不再决定归口部门，仅作分类标签与统计维度。
      
  en: >
      Once the initiator picks the category, no approval node may change it; the only remedy for a wrong classification is rejecting back to the initiator. Category values come from admin-configured dictionary items, no longer determine central ownership, and serve only as a label and reporting dimension.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.203Z"
fingerprint: 2cd70094f1f0ca58486a7545a207061d4af024e2dd777b6b42c457a39f8ec430
source:
  - path: "doc/forms.md"
  - path: "doc/forms.md"
  - path: "doc/prd-0.1.md"
  - path: "oa-server/src/main/java/com/oa/form/matter/MatterFormRules.java"
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

## 证据锚点
- `doc/forms.md` → `### 9.1 路由模型简化（Q10 的连带结论）`（§9.1 路由模型简化）
- `doc/prd-0.1.md` → `REQ-FLOW-025`（§6.3 主干审批链）
