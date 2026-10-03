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
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.460Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-LOG-004`（§6.9 审计日志）
- `doc/data-model.md` → `CREATE TABLE sys_log`（§6. 签名、附件、抄送、消息、审计）
