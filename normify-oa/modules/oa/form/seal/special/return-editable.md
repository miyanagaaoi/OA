---
uid: 9532c987
id: oa.form.seal.special.return-editable
parent: oa.form.seal.special
name: {zh: "审批中可改归还字段", en: "Return Fields Editable in Approval"}
description:
  zh: >
      return_status 与 return_date 是唯一在审批中可改的主字段——因为它们记录事后事实（证照是否已归还），由归档节点⑦填写；此例外必须在服务端状态白名单中显式登记，改「已归还」时必须填归还时间。
      
  en: >
      `return_status` and `return_date` are the only main fields editable during approval because they record a later fact (whether the certificate was returned) and are filled by archive node ⑦; the exception must be registered explicitly in the server-side state whitelist, and marking returned requires a return time.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:58:34.400Z"
fingerprint: 9e01c603eddd74a5a97498d41625d8be347350c66c0db0ae5d7bb17c4c584112
source:
  - path: "doc/forms.md"
  - path: "doc/forms.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/forms/seal/instances/{instance_id}/return/guard"
    description:
      zh: >
          校验仅归档节点可改归还字段。
          
      en: >
          Guards the return fields so only the archive node may edit them.
          
  - protocol: http
    method: PUT
    path: "/api/v1/forms/seal/instances/{instance_id}/return-date"
    description:
      zh: >
          更新归还时间（已归还时必填）。
          
      en: >
          Updates the return time (required when returned).
          
deps:
  - kind: reference
    to: oa.workflow.task
    from_api: "POST /api/v1/forms/seal/instances/{instance_id}/return/guard"
    label: {zh: "归档节点权限", en: "Archive node authority"}
---

## 证据锚点
- `doc/forms.md` → `### 1.2 字段的三态读写模型（**核心约束**）`（§1.2 字段的三态读写模型）
