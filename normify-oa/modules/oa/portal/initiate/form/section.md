---
uid: "19135172"
id: oa.portal.initiate.form.section
parent: oa.portal.initiate.form
state: planned
name: {zh: "分区标题带", en: "Section Header Band"}
description:
  zh: >
      字段分组的整行浅底标题带：canvas-subtle 底、高 32px、typography.label、左内边距 12px、rounded.xs；标题带可折叠且默认展开；折叠只是视觉状态，不影响提交内容与校验范围。
      
  en: >
      Full-width light header band for each field group: canvas-subtle fill, 32px tall, typography.label, 12px left padding, rounded.xs; bands are collapsible and expanded by default; collapsing is purely visual and never changes what is submitted or validated.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.578Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 864
    end_line: 864
apis:
  - protocol: http
    method: GET
    path: "/api/v1/portal/initiate/schema-sections"
    description:
      zh: >
          读取模板分区结构（标题、字段归组、默认折叠态）。
          
      en: >
          Read the template's section structure: titles, field grouping and default collapsed state.
          
deps:
  - kind: call
    to: oa.form.template
    from_api: "GET /api/v1/portal/initiate/schema-sections"
    label: {zh: "读取分区与字段归组", en: "Read sections & grouping"}
  - kind: reference
    to: oa.design.token
    label: {zh: "浅底与圆角令牌", en: "Subtle surface & radius tokens"}
---
