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
      
revision: 966907fad0c5f0d01bc6a76ddba80bbbf67f586a
updated_at: "2026-10-03T04:41:37.396Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
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
