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
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.320Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
    line: 365
    end_line: 365
  - path: "doc/data-model.md"
    line: 437
    end_line: 437
---
