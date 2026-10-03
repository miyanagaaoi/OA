---
uid: 1accfb69
id: oa.portal.initiate.form.fields
parent: oa.portal.initiate.form
state: planned
name: {zh: "字段控件", en: "Field Controls"}
description:
  zh: >
      字段控件集：input / select / date-picker（高 32px、rounded.sm、聚焦保留 2px 焦点环）、textarea（最小 88px、上限 500 字）、checkbox / radio / switch、四级组织 cascader（每级 240px，无权限节点不可见）、金额控件（tnum 右对齐、千分位、两位小数）；只读字段值用 canvas-subtle 底块呈现且不加边框。
      
  en: >
      The field control set: input / select / date-picker (32px tall, rounded.sm, 2px focus ring kept on focus), textarea (min 88px, 500-character cap), checkbox / radio / switch, the four-level organisation cascader (240px per level, nodes outside scope invisible) and the amount control (tnum right-aligned, thousands separators, two decimals); read-only values are shown in a canvas-subtle block without borders.
      
revision: 44fc7aba1c7e884ffa3553faf31bfce974b388a9
updated_at: "2026-10-03T04:12:20.777Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 845
    end_line: 854
  - path: "DESIGN.md"
    line: 862
    end_line: 862
apis:
  - protocol: http
    method: GET
    path: "/api/v1/portal/initiate/schema-fields"
    description:
      zh: >
          字段定义：类型、标签、必填、只读、字段级权限。
          
      en: >
          Field definitions: type, label, required, read-only and field-level permissions.
          
  - protocol: http
    method: POST
    path: "/api/v1/portal/initiate/fields/amount/format"
    description:
      zh: >
          金额输入即时格式化（千分位与两位小数）。
          
      en: >
          Live amount formatting with thousands separators and two decimals.
          
deps:
  - kind: call
    to: oa.form.template
    from_api: "GET /api/v1/portal/initiate/schema-fields"
    label: {zh: "读取字段定义与模板快照版本", en: "Read fields & snapshot version"}
  - kind: call
    to: oa.form.dict
    from_api: "GET /api/v1/portal/initiate/schema-fields"
    label: {zh: "下拉选项取自数据字典", en: "Options from data dictionary"}
  - kind: reference
    to: oa.design.component
    label: {zh: "金额控件规范", en: "Amount control spec"}
---
