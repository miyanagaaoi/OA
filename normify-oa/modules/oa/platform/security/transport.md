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
      
revision: c22d447e6e63ccb0edfd9624026f21e8d1413077
updated_at: "2026-10-03T02:20:07.698Z"
fingerprint: a590145bc5717823c1c716f7cd29b05065b1aa8bf16dddb19dfc3b30d5207ee6
source:
  - path: "doc/prd-0.1.md"
    line: 535
    end_line: 535
  - path: "doc/forms.md"
    line: 58
    end_line: 58
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
