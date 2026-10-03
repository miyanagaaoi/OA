---
uid: 1fb59aab
id: oa.portal.detail.overlay
parent: oa.portal.detail
state: planned
name: {zh: "浮层详情容器", en: "Overlay Detail Container"}
description:
  zh: >
      点击列表行后弹出的居中浮层详情：主表单 2fr / 审批记录 1fr；遮罩固定 40% 黑，点遮罩或 Esc 关闭；有未保存内容时二次确认；不做常驻详情栏，关闭后回到表格原位。
      
  en: >
      The centred overlay opened by clicking a list row: main form 2fr against approval record 1fr; the scrim is a fixed 40% black and the overlay closes on scrim click or Esc; closing with unsaved content asks for a second confirmation; no permanent detail column, and closing returns to the same table position.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.570Z"
fingerprint: d7d0d1b9e6e41e9590b00d6806c9c43da38139e084006a3b5c0ed7a820865bdb
source:
  - path: "doc/prd-0.1.md"
  - path: "DESIGN.md"
    line: 897
    end_line: 897
  - path: "DESIGN.md"
    line: 810
    end_line: 810
apis:
  - protocol: http
    method: GET
    path: "/api/v1/portal/detail/{instance_id}"
    description:
      zh: >
          加载流程实例、节点状态与权限过滤后的字段。
          
      en: >
          Load the flow instance, its node states and permission-filtered fields.
          
  - protocol: http
    method: GET
    path: "/detail/{instance_id}"
    description:
      zh: >
          从列表行就地打开发布层的详情路由（不跳页）。
          
      en: >
          Overlay detail route opened from a list row without leaving the page.
          
deps:
  - kind: call
    to: oa.workflow.runtime
    from_api: "GET /api/v1/portal/detail/{instance_id}"
    label: {zh: "读取流程实例与节点状态", en: "Read instance & node state"}
---

## 证据锚点
- `doc/prd-0.1.md` → `### 13.2 与审批业务强相关的约定`（§13.2 与审批业务强相关的约定）
