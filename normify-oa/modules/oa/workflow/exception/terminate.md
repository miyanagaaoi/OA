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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.805Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 393
    end_line: 393
  - path: "doc/prd-0.1.md"
    line: 354
    end_line: 354
  - path: "doc/prd-0.1.md"
    line: 482
    end_line: 482
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
