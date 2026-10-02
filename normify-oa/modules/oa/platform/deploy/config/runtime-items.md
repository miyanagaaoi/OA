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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.763Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
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
