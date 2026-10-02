---
uid: 21d0c8e6
id: oa.workflow.task.addsign
parent: oa.workflow.task
state: planned
name: {zh: "加签", en: "Add-sign"}
description:
  zh: >
      审批人可在审批时添加临时审批人的两种方式：前加签由加签人先审、审完回到本人；后加签由本人审完后再交加签人审。加签人必须签署意见，加签行为与加签链记入审计日志；加签链存于节点实例的 add_sign_chain_json 字段。
      
  en: >
      Two ways for an approver to add a temporary approver: add-sign before inserts the signer ahead of the current approver, add-sign after hands the task over once the current approver has passed. Added signers must give an opinion; the chain is stored on the node instance and audited.
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.829Z"
fingerprint: 6be246c6834b9b56d4a42d5b955150bb6a06a766a90c031fd7f3af6b4c917a6d
source:
  - path: "doc/prd-0.1.md"
    line: 346
    end_line: 346
  - path: "doc/prd-0.1.md"
    line: 392
    end_line: 392
  - path: "doc/data-model.md"
    line: 437
    end_line: 437
---
