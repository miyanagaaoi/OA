---
uid: "31787203"
id: oa.archive.policy.purge
parent: oa.archive.policy
state: planned
name: {zh: "清理与评审留痕", en: "Purge Control"}
description:
  zh: >
      一期不做物理删除；如需清理须提交清理申请、单独评审并写入审计记录，系统按申请单约束执行范围。
      
  en: >
      No physical deletion in phase one; any cleanup must go through a purge request, a separate review and an audit record, and the system enforces the approved scope.
      
revision: 44fc7aba1c7e884ffa3553faf31bfce974b388a9
updated_at: "2026-10-03T04:12:20.666Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/data-model.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/archive/purge-requests"
    description:
      zh: >
          提交物理清理申请并附评审依据。
          
      en: >
          Submits a physical purge request for review.
          
  - protocol: http
    method: GET
    path: "/api/v1/archive/purge-requests"
    description:
      zh: >
          查看清理申请与评审状态。
          
      en: >
          Lists purge requests and their review status.
          
deps:
  - kind: reference
    to: oa.admin.boundary
    from_api: "POST /api/v1/archive/purge-requests"
    label: {zh: "不可删除单据与日志", en: "No deleting docs or logs"}
  - kind: reference
    to: oa.audit.integrity.retention
    from_api: "POST /api/v1/archive/purge-requests"
    label: {zh: "保留期到期约束", en: "Retention expiry constraint"}
  - kind: reference
    to: oa.audit.oplog.capture
    from_api: "POST /api/v1/archive/purge-requests"
    label: {zh: "清理评审留痕", en: "Log the purge review"}
---

## 证据锚点
- `doc/data-model.md` → `## 10. 归档策略（对应 REQ-NFR-010）`（§10. 归档策略）
- `doc/prd-0.1.md` → `REQ-NFR-010`（§第9章 非功能需求）
