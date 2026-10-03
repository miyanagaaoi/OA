---
uid: a2b18498
id: oa.platform.deploy.config.runtime-items
parent: oa.platform.deploy.config
state: planned
name: {zh: "运行期可配置项", en: "Runtime-Tunable Items"}
description:
  zh: >
      运营期可调整的业务配置项清单：决议模式、会签阈值、节点超时、流转与回退上限、补件上限、强制签名节点、会话与锁定规则、审计保留期、导出权限。
      
  en: >
      The catalogue of business items operations may tune at runtime: decision mode, countersign threshold, node timeout, routing and rollback caps, supplement limits, forced-signature nodes, session and lockout rules, audit retention, export permission.
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.639Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: file
    path: "config/configurable-items.yml"
    description:
      zh: >
          运行期可配置项清单与默认值。
          
      en: >
          Catalogue of runtime-adjustable items and their defaults.
          
  - protocol: http
    method: GET
    path: "/api/v1/admin/config-items"
    description:
      zh: >
          查询运行期可配置项的当前生效值。
          
      en: >
          Reads the effective value of runtime-adjustable items.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `### 9.1 可配置项汇总（运营期由管理员调整，不经开发）`（§9.1 可配置项汇总）
