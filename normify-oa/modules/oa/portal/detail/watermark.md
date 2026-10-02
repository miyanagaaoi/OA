---
uid: 3ea5a2e5
id: oa.portal.detail.watermark
parent: oa.portal.detail
state: planned
name: {zh: "详情页水印", en: "Detail Watermark"}
description:
  zh: >
      详情页水印层：typography.caption + 墨色 5%–8% 透明度，旋转 -24°，内容为「姓名 + 工号」，间距 240×160px；pointer-events: none 且 user-select: none，不得遮挡按钮与表单值（REQ-USER-004）。
      
  en: >
      The detail-page watermark layer: typography.caption in ink at 5%–8% opacity, rotated -24°, tiled every 240×160px with the user's name and employee number; pointer-events and user-select are both none, and it must never cover buttons or form values (REQ-USER-004).
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.739Z"
fingerprint: 28e8829672cee9b922026028eb18feb80adbb9de4b7fc02f0910f372f46c48c2
source:
  - path: "DESIGN.md"
    line: 900
    end_line: 900
  - path: "doc/prd-0.1.md"
    line: 418
    end_line: 418
apis:
  - protocol: http
    method: GET
    path: "/api/v1/portal/watermark/profile"
    description:
      zh: >
          当前用户的水印文本（姓名 + 工号）与透明度区间。
          
      en: >
          Watermark text (name plus employee number) and opacity range for the current user.
          
deps:
  - kind: call
    to: oa.identity.user
    from_api: "GET /api/v1/portal/watermark/profile"
    label: {zh: "姓名与工号", en: "Name & employee number"}
---
