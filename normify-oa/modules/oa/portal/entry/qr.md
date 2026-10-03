---
uid: 50a6ae11
id: oa.portal.entry.qr
parent: oa.portal.entry
state: planned
name: {zh: "移动端入口二维码", en: "Mobile Entry QR Code"}
description:
  zh: >
      OA 首页提供的移动端入口二维码（REQ-USER-001）：零成本、无第三方对接；二维码指向短链，扫码进入 H5；提供下载 / 打印用图与失效刷新，并展示入口说明。
      
  en: >
      The mobile entry QR code offered on the OA home page (REQ-USER-001): zero cost and no third-party integration; the code points at a short link that opens the H5 portal, and the page offers a downloadable and printable image plus expiry refresh and entry instructions.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.572Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/portal/entry/qr"
    description:
      zh: >
          移动端入口二维码内容与落地地址。
          
      en: >
          Mobile entry QR code payload and landing URL.
          
  - protocol: file
    path: "entry/h5-qr.png"
    description:
      zh: >
          可下载与打印的二维码图片资产（零第三方依赖）。
          
      en: >
          QR image asset for download and printing (zero third-party dependency).
          
deps:
  - kind: call
    to: oa.portal.h5
    from_api: "GET /api/v1/portal/entry/qr"
    label: {zh: "二维码指向 H5 入口", en: "QR points to H5 entry"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-USER-001`（§6.8 移动端 H5 与登录保持）
