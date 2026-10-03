---
uid: 11e30b53
id: oa.identity.user.watermark
parent: oa.identity.user
name: {zh: "水印身份要素", en: "Watermark Identity Elements"}
description:
  zh: >
      提供「姓名 + 工号」水印要素与样式参数（透明度 5%–8%、旋转 -24°、间距 240×160px、不遮挡按钮与表单值），供 H5 与单据详情页渲染（REQ-USER-004）。
      
  en: >
      Provides the name plus employee-number watermark elements and style parameters (5–8% opacity, -24° rotation, 240x160px spacing, never covering buttons or form values) for the H5 and document-detail surfaces.
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.384Z"
fingerprint: d7d0d1b9e6e41e9590b00d6806c9c43da38139e084006a3b5c0ed7a820865bdb
source:
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-USER-004`（§6.8 移动端 H5 与登录保持）
