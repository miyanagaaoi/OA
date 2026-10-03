---
uid: 74800d3b
id: oa.workflow.exception.terminate
parent: oa.workflow.exception
state: planned
name: {zh: "终止", en: "Terminate"}
description:
  zh: >
      系统管理员与集团分管领导可终止流程，必须填写原因；终止后实例状态置「已终止」（终态，不可再提交），全部进行中节点实例与任务取消或自动关闭，并通知发起人；终止动作写入审计日志。
      
  en: >
      System administrators and the group line leader may terminate a flow and must give a reason. The instance becomes terminated, a final state that can never be submitted again; every processing node instance and task is cancelled or auto-closed and the initiator is notified. The action is written to the audit log.
      
revision: 44fc7aba1c7e884ffa3553faf31bfce974b388a9
updated_at: "2026-10-03T04:12:20.805Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/instances/{instance_id}/terminate"
    description:
      zh: >
          终止流程（原因必填，终态不可再提交）。
          
      en: >
          Terminate the flow with a mandatory reason; the state is final.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow/instances/{instance_id}/terminable"
    description:
      zh: >
          判定当前用户是否具备终止权限。
          
      en: >
          Check whether the current user may terminate.
          
deps:
  - kind: call
    to: oa.workflow.runtime
    from_api: "POST /api/v1/flow/instances/{instance_id}/terminate"
    label: {zh: "置终态并关闭其余任务", en: "Set terminal, close tasks"}
  - kind: call
    to: oa.audit.oplog
    from_api: "POST /api/v1/flow/instances/{instance_id}/terminate"
    label: {zh: "终止留痕", en: "Audit the termination"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-010`（§6.3.1 集团层流转机制）
