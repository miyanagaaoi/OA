---
uid: 48a5b914
id: oa.portal.h5.watermark
parent: oa.portal.h5
state: planned
name: {zh: "H5 水印层", en: "H5 Watermark Layer"}
description:
  zh: >
      H5 与详情页共用的水印层实现：姓名 + 工号，5%–8% 透明度、-24° 旋转、间距 240×160px、caption 字号；水印覆盖全屏但不拦截事件（pointer-events: none），不得遮挡底部操作栏按钮与表单值。
      
  en: >
      The watermark layer shared by H5 and the detail page: name plus employee number, 5%–8% opacity, rotated -24° and tiled every 240×160px at caption size; it covers the screen without intercepting events (pointer-events none) and must never cover the bottom action bar buttons or form values.
      
revision: e3b34a3c59417096ade647fab4261b06f6b605e2
updated_at: "2026-10-03T02:30:28.777Z"
fingerprint: cd9efea98e4b8fd30bd3cc4099150a74c191c187c756c15dfd65a60a05dc8cbc
source:
  - path: "DESIGN.md"
    line: 900
    end_line: 900
  - path: "doc/prd-0.1.md"
    line: 418
    end_line: 418
  - path: "doc/prd-0.1.md"
    line: 666
    end_line: 666
apis:
  - protocol: http
    method: GET
    path: "/api/v1/portal/h5/watermark"
    description:
      zh: >
          H5 与详情页共用的水印文本与透明度参数。
          
      en: >
          Watermark copy and opacity parameters for H5 and detail pages.
          
deps:
  - kind: call
    to: oa.identity.user
    from_api: "GET /api/v1/portal/h5/watermark"
    label: {zh: "姓名与工号", en: "Name & employee number"}
---
