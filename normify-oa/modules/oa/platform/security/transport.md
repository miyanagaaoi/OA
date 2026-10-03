---
uid: 95befa9f
id: oa.platform.security.transport
parent: oa.platform.security
state: planned
name: {zh: "传输安全", en: "Transport Security"}
description:
  zh: >
      全站 HTTPS 与现代密码套件、HSTS 与安全响应头；附件存私有化本地存储，未经鉴权接口不得直链下载。
      
  en: >
      Site-wide HTTPS with modern cipher suites, HSTS and security response headers, and private storage that refuses direct attachment links without authentication.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.392Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: file
    path: "deploy/nginx/tls.conf"
    description:
      zh: >
          HTTPS 与密码套件配置。
          
      en: >
          HTTPS and cipher-suite configuration.
          
  - protocol: file
    path: "deploy/nginx/hsts.conf"
    description:
      zh: >
          HSTS 与安全响应头配置。
          
      en: >
          HSTS and security response headers.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-NFR-005`（§第9章 非功能需求）
