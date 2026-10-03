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
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.356Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `### 8.1 接口设计原则`（§8.1 接口设计原则）
