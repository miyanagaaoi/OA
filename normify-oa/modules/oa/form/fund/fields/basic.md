---
uid: 3d6fdcb8
id: oa.form.fund.fields.basic
parent: oa.form.fund.fields
name: {zh: "资金基础字段组", en: "Fund Basic Field Group"}
description:
  zh: >
      资金事由 title（text≤60、必填）、事项分类 category（select、配置项、默认「经济」、不参与路由）、申请金额 amount（amount、必填、> 0，为 0 或空时禁止提交）；金额以 DECIMAL(18,2) 存储。
      
  en: >
      Fund reason `title` (text ≤60, required), category `category` (select, configurable, defaults to economy, does not drive routing) and requested amount `amount` (amount, required, > 0; zero or empty blocks submission), stored as DECIMAL(18,2).
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.265Z"
fingerprint: 1266f6407e434b7ee473c37529842b35c2605a39bc4ad8881f6bc19d40d4caf1
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
