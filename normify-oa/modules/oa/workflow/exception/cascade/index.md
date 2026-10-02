---
uid: 61dcd426
id: oa.workflow.exception.cascade
parent: oa.workflow.exception
state: planned
name: {zh: "驳回联动关闭", en: "Rejection Cascade & Closing"}
description:
  zh: >
      驳回发生后的联动关闭：会签节点中任一人驳回即节点立即驳回、其余未处理任务自动关闭（REQ-FLOW-015）；任一协同部门驳回则单据回到发起人，其余协同任务自动关闭（REQ-FLOW-016）；节点与实例的其余任务、其余节点实例按状态机联动取消或关闭，防止挂单。
      
  en: >
      Cascade closing after a rejection: on a countersign node any single rejection rejects the node immediately and auto-closes its remaining tasks; a rejection from any collaborating department returns the document to the initiator and closes the other collaboration tasks. Remaining tasks and node instances follow the state machine so no document is left hanging.
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.803Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 386
    end_line: 387
  - path: "doc/prd-0.1.md"
    line: 496
    end_line: 496
---
