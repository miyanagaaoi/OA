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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.818Z"
fingerprint: 1ebf4aee4ac8648bfaec0ea0afbfecc1b3e2f5fecc0dff532be914d9e85be0f2
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
