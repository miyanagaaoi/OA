---
uid: aa8fd28c
id: oa.design.component.navigation
parent: oa.design.component
state: planned
name: {zh: "导航组件", en: "Navigation"}
description:
  zh: >
      导航族：sidebar（224px / 收起 64px 深色左栏，顶部组织切换器 48px，底部用户区）、sidebar-item-active（inverse-surface-1 底 + 左侧 2px primary 指示条）、sidebar-tree-node（四级组织树，缩进 12px/级，高 32px）、topbar（56px，白底 + 1px 底边，不做全局搜索框）、breadcrumb 与 h5-bottom-action-bar（60px + 安全区）。
      
  en: >
      The navigation family: the sidebar (224px, or a 64px rail, dark, with a 48px org switcher on top and the user block below), the active item (inverse-surface-1 fill with a 2px primary bar on the left), the four-level org tree node (12px indent per level, 32px tall), the 56px top bar (white with a 1px bottom border and no global search), breadcrumbs and the 60px H5 bottom action bar with safe-area padding.
      
revision: 44fc7aba1c7e884ffa3553faf31bfce974b388a9
updated_at: "2026-10-03T04:12:20.691Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 886
    end_line: 893
  - path: "DESIGN.md"
    line: 934
    end_line: 934
apis:
  - protocol: file
    path: "styles/components/navigation.css"
    description:
      zh: >
          导航样式：侧栏、图标条、选中项、组织树节点、顶栏、面包屑与 H5 底栏。
          
      en: >
          Navigation CSS: sidebar, rail, active item, org tree node, top bar, breadcrumb and H5 bar.
          
deps:
  - kind: reference
    to: oa.design.token.color.inverse
    from_api: "file:styles/components/navigation.css"
    to_api: "file:styles/tokens/color-inverse.css"
    label: {zh: "侧栏反向表面", en: "Sidebar inverse surfaces"}
---
