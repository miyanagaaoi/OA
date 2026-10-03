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
      
revision: c22d447e6e63ccb0edfd9624026f21e8d1413077
updated_at: "2026-10-03T02:20:07.691Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 544
    end_line: 561
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
