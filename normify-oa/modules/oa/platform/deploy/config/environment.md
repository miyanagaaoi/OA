---
uid: 94241df9
id: oa.platform.deploy.config.environment
parent: oa.platform.deploy.config
name: {zh: "环境设置", en: "Environment Settings"}
description:
  zh: >
      按环境区分的设置：数据库连接、文件存储根、邮件中继与本地资源基路径。
      
  en: >
      Per-environment settings: database connection, file storage root, mail relay and the local asset base path.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.096Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
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
