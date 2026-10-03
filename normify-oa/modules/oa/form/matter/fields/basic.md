---
uid: 2d746dee
id: oa.form.matter.fields.basic
parent: oa.form.matter.fields
name: {zh: "基础信息字段组", en: "Basic Field Group"}
description:
  zh: >
      事项标题 title（text≤60、必填、发起后只读）、事项类别 category（select、配置项、提交后不可改判）、事项描述 description（textarea≤2000、≥10 字符）；三项均在提交后只读。
      
  en: >
      Matter title `title` (text ≤60, required, read-only after initiation), category `category` (select, configuration item, not re-classifiable after submission) and description `description` (textarea ≤2000, ≥10 characters); all three become read-only after submission.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.269Z"
fingerprint: 1266f6407e434b7ee473c37529842b35c2605a39bc4ad8881f6bc19d40d4caf1
source:
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/forms/matter/field-groups/basic"
    description:
      zh: >
          事项单基础信息字段组定义。
          
      en: >
          Basic field group definition for matter forms.
          
  - protocol: http
    method: PUT
    path: "/api/v1/forms/matter/instances/{instance_id}/draft/basic"
    description:
      zh: >
          保存基础信息字段草稿。
          
      en: >
          Saves the basic field group draft.
          
---

## 证据锚点
- `doc/forms.md` → `## 2. 事项审批单（`form_type = matter`）`（§2. 事项审批单）
