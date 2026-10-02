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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.730Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 531
    end_line: 531
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
