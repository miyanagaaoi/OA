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
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.650Z"
fingerprint: 6be246c6834b9b56d4a42d5b955150bb6a06a766a90c031fd7f3af6b4c917a6d
source:
  - path: "doc/data-model.md"
    line: 827
    end_line: 828
  - path: "doc/prd-0.1.md"
    line: 440
    end_line: 440
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
