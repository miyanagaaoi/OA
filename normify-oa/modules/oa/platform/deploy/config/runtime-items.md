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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.713Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
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
