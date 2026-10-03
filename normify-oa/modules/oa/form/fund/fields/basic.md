---
uid: 3d6fdcb8
id: oa.form.fund.fields.basic
parent: oa.form.fund.fields
state: planned
name: {zh: "资金基础字段组", en: "Fund Basic Field Group"}
description:
  zh: >
      资金事由 title（text≤60、必填）、事项分类 category（select、配置项、默认「经济」、不参与路由）、申请金额 amount（amount、必填、> 0，为 0 或空时禁止提交）；金额以 DECIMAL(18,2) 存储。
      
  en: >
      Fund reason `title` (text ≤60, required), category `category` (select, configurable, defaults to economy, does not drive routing) and requested amount `amount` (amount, required, > 0; zero or empty blocks submission), stored as DECIMAL(18,2).
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.344Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/fund/field-groups/basic"
    description:
      zh: >
          资金单基础字段组定义。
          
      en: >
          Basic field group definition for fund forms.
          
  - protocol: http
    method: PUT
    path: "/api/v1/forms/fund/instances/{instance_id}/draft/basic"
    description:
      zh: >
          保存资金单基础字段草稿。
          
      en: >
          Saves the fund basic field group draft.
          
  - protocol: http
    method: POST
    path: "/api/v1/forms/fund/fields/amount/submit-guard"
    description:
      zh: >
          金额为 0 或空时阻断提交。
          
      en: >
          Blocks submission when the amount is zero or empty.
          
---

## 证据锚点
- `doc/forms.md` → `## 3. 资金审批单（`form_type = fund`）`（§3. 资金审批单）
