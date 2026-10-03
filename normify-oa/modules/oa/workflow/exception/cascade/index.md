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
      
revision: 112ab0a1d46779714029044fc8e0b46627804f30
updated_at: "2026-10-03T01:51:21.700Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 386
    end_line: 387
  - path: "doc/prd-0.1.md"
    line: 496
    end_line: 496
---
