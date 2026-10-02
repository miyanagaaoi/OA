---
uid: 2d746dee
id: oa.form.matter.fields.basic
parent: oa.form.matter.fields
state: planned
name: {zh: "基础信息字段组", en: "Basic Field Group"}
description:
  zh: >
      事项标题 title（text≤60、必填、发起后只读）、事项类别 category（select、配置项、提交后不可改判）、事项描述 description（textarea≤2000、≥10 字符）；三项均在提交后只读。
      
  en: >
      Matter title `title` (text ≤60, required, read-only after initiation), category `category` (select, configuration item, not re-classifiable after submission) and description `description` (textarea ≤2000, ≥10 characters); all three become read-only after submission.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.664Z"
fingerprint: 55b9e9a4e4cd28138ccadef569af0c2127d2c6e4f1c628f3e34e8c72c9f8dbe2
source:
  - path: "doc/forms.md"
    line: 79
    end_line: 81
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
