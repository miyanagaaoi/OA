---
uid: 05d10f25
id: oa.audit.oplog.diff
parent: oa.audit.oplog
state: planned
name: {zh: "配置变更前后值", en: "Config Change Diff"}
description:
  zh: >
      组织、用户、数据字典、流程模板等配置类变更在写入日志时生成变更前后 JSON 快照；缺失前后值时拒绝落库，保证「谁改了什么、从什么改成什么」可复原。
      
  en: >
      Configuration changes (org, user, dictionary, flow template) produce before/after JSON snapshots when logged; writes without both snapshots are rejected so every change is reconstructable.
      
revision: 112ab0a1d46779714029044fc8e0b46627804f30
updated_at: "2026-10-03T01:51:21.547Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/prd-0.1.md"
    line: 426
    end_line: 429
  - path: "doc/data-model.md"
    line: 654
    end_line: 655
apis:
  - protocol: http
    method: POST
    path: "/api/v1/audit/config-changes"
    description:
      zh: >
          记录一条配置变更的前后值。
          
      en: >
          Records before/after values of a configuration change.
          
  - protocol: http
    method: GET
    path: "/api/v1/audit/config-changes/{id}/diff"
    description:
      zh: >
          查看指定配置变更的前后值对比。
          
      en: >
          Returns the before/after diff of a configuration change.
          
deps:
  - kind: call
    to: oa.admin.dict
    from_api: "POST /api/v1/audit/config-changes"
    label: {zh: "字典项变更前后值", en: "Dictionary change snapshot"}
  - kind: call
    to: oa.workflow.definition
    from_api: "POST /api/v1/audit/config-changes"
    label: {zh: "流程模板发布前后值", en: "Flow template publish snapshot"}
  - kind: dataflow
    to: oa.audit.oplog.capture
    from_api: "POST /api/v1/audit/config-changes"
    to_api: "mysql:sys_log"
    label: {zh: "快照随日志只追加落库", en: "Append snapshot to log"}
---
