---
uid: 3ea5a2e5
id: oa.portal.detail.watermark
parent: oa.portal.detail
name: {zh: "详情页水印", en: "Detail Watermark"}
description:
  zh: >
      详情页水印层：typography.caption + 墨色 5%–8% 透明度，旋转 -24°，内容为「姓名 + 工号」，间距 240×160px；pointer-events: none 且 user-select: none，不得遮挡按钮与表单值（REQ-USER-004）。
      
  en: >
      The detail-page watermark layer: typography.caption in ink at 5%–8% opacity, rotated -24°, tiled every 240×160px with the user's name and employee number; pointer-events and user-select are both none, and it must never cover buttons or form values (REQ-USER-004).
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.338Z"
fingerprint: 6f97dc580ed4d0d87fae8063d855678f9a2f3b3425b149dd9321be8c4b605b79
source:
  - path: "DESIGN.md"
    line: 900
    end_line: 900
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-USER-004`（§6.8 移动端 H5 与登录保持）
