---
uid: 6bb52d7e
id: oa.platform.deploy.localize
parent: oa.platform.deploy
state: planned
name: {zh: "本地化资源", en: "Localised Assets"}
description:
  zh: >
      字体、库文件与静态资源全部由本地部署提供，不从任何公网 CDN 或云服务拉取。
      
  en: >
      Fonts, libraries and static assets are all served from the local deployment; nothing is fetched from public CDNs or cloud services.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.388Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: file
    path: "deploy/assets/fonts/inter.woff2"
    description:
      zh: >
          本地自托管字体文件。
          
      en: >
          Locally hosted font file.
          
  - protocol: file
    path: "deploy/assets/libs/"
    description:
      zh: >
          本地第三方前端库目录。
          
      en: >
          Directory of locally vendored front-end libraries.
          
  - protocol: http
    method: GET
    path: "/assets/{path}"
    description:
      zh: >
          静态资源本地化访问入口。
          
      en: >
          Local static asset endpoint.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-NFR-001`（§第9章 非功能需求）
