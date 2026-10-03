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
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.770Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 415
    end_line: 415
  - path: "doc/prd-0.1.md"
    line: 637
    end_line: 637
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
