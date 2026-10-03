---
uid: 423d3ba6
id: oa.portal.h5.actionbar
parent: oa.portal.h5
state: planned
name: {zh: "H5 底部操作栏", en: "H5 Bottom Action Bar"}
description:
  zh: >
      H5 底部操作栏：高 60px 常驻、白底 + 1px 上边框（不用阴影，避免滚动闪烁），适配 env(safe-area-inset-bottom)；同意为主按钮占满剩余宽度，拒绍为 96px 次按钮，「更多」（转办 / 加签）为文本按钮。
      
  en: >
      The H5 bottom action bar: a persistent 60px band, white with a 1px top border instead of a shadow to avoid flicker while scrolling, padded for env(safe-area-inset-bottom); approve is the primary button filling the remaining width, reject is a fixed 96px secondary button and More (transfer / countersign) is a text button.
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.383Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 893
    end_line: 893
  - path: "DESIGN.md"
    line: 843
    end_line: 843
  - path: "DESIGN.md"
    line: 812
    end_line: 812
apis:
  - protocol: http
    method: POST
    path: "/api/v1/portal/h5/instances/{instance_id}/actions"
    description:
      zh: >
          从 H5 底部操作栏提交审批动作。
          
      en: >
          Submit an approval action from the H5 bottom bar.
          
deps:
  - kind: call
    to: oa.workflow.task
    from_api: "POST /api/v1/portal/h5/instances/{instance_id}/actions"
    label: {zh: "提交 H5 审批动作", en: "Submit H5 approval action"}
---
