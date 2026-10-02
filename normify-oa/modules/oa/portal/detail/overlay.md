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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.721Z"
fingerprint: 877c10520a96987f02d2eb2349ebc1ee00166b72c021c7aa6829111b4e162e43
source:
  - path: "doc/prd-0.1.md"
    line: 667
    end_line: 669
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
