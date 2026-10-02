---
uid: "48971369"
id: oa.portal.h5.touch
parent: oa.portal.h5
state: planned
name: {zh: "H5 触控尺寸", en: "H5 Touch Targets"}
description:
  zh: >
      H5 触控与控件尺寸：可点击元素与控件高度不低于 44px（spacing.control-h5），列表用卡片列表（不做表格），字段单列、标签在上；正文仍为 14px，不因触控目标放大而降低密度。
  en: >
      H5 touch and control sizing: every clickable element and control is at least 44px tall (spacing.control-h5) and lists become card lists rather than tables; form fields are single-column with the label above the control; body text stays 14px so larger targets do not cost density.
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:10:00Z"
fingerprint: pending
source:
  - path: "DESIGN.md"
    line: 724
    end_line: 724
  - path: "doc/prd-0.1.md"
    line: 668
    end_line: 668
  - path: "doc/prd-0.1.md"
    line: 666
    end_line: 666
apis:
  - protocol: http
    method: GET
    path: "/m/portal/todo"
    description:
      zh: >
          H5 待办卡片列表页。
      en: >
          H5 pending task card list route.
deps:
  - kind: dataflow
    to: oa.workflow.task
    from_api: "GET /m/portal/todo"
    label: {zh: "卡片列表取任务数据", en: "Card list from tasks"}
---
