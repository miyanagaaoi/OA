---
uid: a5b3ce6e
id: oa.integration.masterdata
parent: oa.integration
name: {zh: "主数据只读开放", en: "Master Data Read-Only API"}
description:
  zh: >
      用户/组织架构作为主数据源，向后续 HR 系统等开放只读查询（OpenAPI 仅开放组织与用户只读），在权限模型成熟前不引入外部写入风险。
      
  en: >
      Organisation and user data is the master source and is exposed read-only to downstream systems such as HR, keeping the permission model closed to external mutation in phase one.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.313Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `### 8.1 接口设计原则`（§8.1 接口设计原则）
