---
uid: 6bb52d7e
id: oa.platform.deploy.localize
parent: oa.platform.deploy
name: {zh: "本地化资源", en: "Localised Assets"}
description:
  zh: >
      字体、库文件与静态资源全部由本地部署提供，不从任何公网 CDN 或云服务拉取。
      
  en: >
      Fonts, libraries and static assets are all served from the local deployment; nothing is fetched from public CDNs or cloud services.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.328Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
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
