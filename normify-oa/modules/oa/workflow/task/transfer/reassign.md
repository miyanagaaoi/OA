---
uid: 21290ac5
id: oa.workflow.task.transfer.reassign
parent: oa.workflow.task.transfer
state: planned
name: {zh: "改派", en: "Reassignment"}
description:
  zh: >
      仅系统管理员可改派任务，用于快照审批人离职、调岗或不可用的场景；必须填写改派原因并留痕（任务状态置已改派 + 操作日志）。一期在途单据不改派，改派是审批人快照策略的必要补偿控制。
      
  en: >
      Only system administrators may reassign a task, used when a snapshot approver has left or become unavailable; a reason is mandatory and every reassignment is audited. In-flight documents are never reassigned automatically - reassignment compensates for the immutable approver snapshot.
      
revision: 44fc7aba1c7e884ffa3553faf31bfce974b388a9
updated_at: "2026-10-03T04:12:20.825Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/tasks/{task_id}/reassign"
    description:
      zh: >
          管理员改派任务（原因必填并留痕）。
          
      en: >
          Administrator reassignment with a mandatory audited reason.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow/tasks/pending-reassign"
    description:
      zh: >
          快照审批人不可用的待改派任务清单。
          
      en: >
          Tasks whose snapshot approver is unavailable.
          
deps:
  - kind: call
    to: oa.audit.oplog
    from_api: "POST /api/v1/flow/tasks/{task_id}/reassign"
    label: {zh: "改派留痕", en: "Audit the reassignment"}
  - kind: reference
    to: oa.identity.user
    from_api: "GET /api/v1/flow/tasks/pending-reassign"
    label: {zh: "人员离职调岗查询", en: "Check user leave/transfer"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-019`（§6.6 异常路径）
