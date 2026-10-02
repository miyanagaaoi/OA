---
uid: "9318e201"
id: oa.authz.visibility.readonly-state
parent: oa.authz.visibility
state: planned
name: {zh: "只读可见与可审批判定", en: "Read-only & Approvability"}
description:
  zh: >
      抄送人只读可见且不产生待办；待补件期间单据对其他角色保持只读可见但不可审批（避免待补件单据被人审掉），补件仅限发起人提交。
      
  en: >
      CC users see documents read-only and get no todo; while a document awaits supplements it stays read-only for other roles and cannot be approved, so a pending-supplement document cannot be signed off by someone else.
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.696Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 312
    end_line: 333
  - path: "doc/prd-0.1.md"
    line: 401
    end_line: 409
apis:
  - protocol: rpc
    path: "authz.visibility.canApprove"
    description:
      zh: >
          判定用户对单据当前状态是否可审批。
          
      en: >
          Tells whether the user may approve now.
          
  - protocol: http
    method: GET
    path: "/api/v1/authz/readonly-reasons"
    description:
      zh: >
          返回只读可见的具体原因（抄送/待补件等）。
          
      en: >
          Returns the read-only reason such as CC or pending supplement.
          
deps:
  - kind: dataflow
    to: oa.workflow.runtime
    label: {zh: "读取子状态与抄送关系", en: "Read sub-status and CC"}
---
