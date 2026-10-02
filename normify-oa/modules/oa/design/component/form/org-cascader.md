---
uid: 9f68293b
id: oa.design.component.form.org-cascader
parent: oa.design.component.form
state: planned
name: {zh: "组织四级联级选择器", en: "Org Cascader"}
description:
  zh: >
      组织架构选择器：集团—公司—部门—科室四级联级面板，每级 240px 宽，支持搜索定位；只在数据权限边界内展示节点——无权限的组织节点不可见而非置灰，避免通过选择器探测组织架构（对应 5.2 权限模型）。
      
  en: >
      The organisation picker: a four-level cascade from group to company to department to section, 240px wide per level with search; it only renders nodes inside the caller's data scope — out-of-scope nodes are invisible rather than disabled, so the picker cannot be used to probe the org chart.
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.682Z"
fingerprint: ac0ee794ab636d5631ecf55f3fe0063493d1e3cafae26bb19c1ca5c017904aea
source:
  - path: "DESIGN.md"
    line: 851
    end_line: 851
  - path: "DESIGN.md"
    line: 911
    end_line: 911
apis:
  - protocol: file
    path: "styles/components/cascader.css"
    description:
      zh: >
          组织联级选择器样式：每级 240px 宽的四级面板，可见性受数据权限约束。
          
      en: >
          Org cascader CSS: four-level panels 240px wide with permission-filtered visibility.
          
deps:
  - kind: reference
    to: oa.authz.scope
    label: {zh: "无权限节点不可见", en: "Scope-filtered org nodes"}
---
