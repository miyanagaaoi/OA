---
uid: 94241df9
id: oa.platform.deploy.config.environment
parent: oa.platform.deploy.config
state: planned
name: {zh: "环境设置", en: "Environment Settings"}
description:
  zh: >
      按环境区分的设置：数据库连接、文件存储根、邮件中继与本地资源基路径。
      
  en: >
      Per-environment settings: database connection, file storage root, mail relay and the local asset base path.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.387Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: file
    path: "config/application.yml"
    description:
      zh: >
          应用环境配置。
          
      en: >
          Application environment configuration.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-NFR-001`（§第9章 非功能需求）
