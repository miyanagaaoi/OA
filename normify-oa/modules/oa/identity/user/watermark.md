---
uid: 11e30b53
id: oa.identity.user.watermark
parent: oa.identity.user
state: planned
name: {zh: "水印身份要素", en: "Watermark Identity Elements"}
description:
  zh: >
      提供「姓名 + 工号」水印要素与样式参数（透明度 5%–8%、旋转 -24°、间距 240×160px、不遮挡按钮与表单值），供 H5 与单据详情页渲染（REQ-USER-004）。
      
  en: >
      Provides the name plus employee-number watermark elements and style parameters (5–8% opacity, -24° rotation, 240x160px spacing, never covering buttons or form values) for the H5 and document-detail surfaces.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.694Z"
fingerprint: 877c10520a96987f02d2eb2349ebc1ee00166b72c021c7aa6829111b4e162e43
source:
  - path: "doc/prd-0.1.md"
    line: 413
    end_line: 420
  - path: "DESIGN.md"
    line: 896
    end_line: 912
apis:
  - protocol: http
    method: GET
    path: "/api/v1/identity/users/me/watermark"
    description:
      zh: >
          当前登录人的姓名与工号水印数据。
          
      en: >
          Watermark data of the current user.
          
  - protocol: http
    method: GET
    path: "/api/v1/identity/watermark-policy"
    description:
      zh: >
          水印透明度/旋转/间距策略参数。
          
      en: >
          Watermark opacity, rotation and spacing policy.
          
deps:
  - kind: call
    to: oa.identity.user.profile
    from_api: "GET /api/v1/identity/users/me/watermark"
    to_api: "GET /api/v1/identity/users"
    label: {zh: "读取姓名与工号", en: "Read name and employee no"}
---
