---
uid: 61dcd426
id: oa.workflow.exception.cascade
parent: oa.workflow.exception
name: {zh: "驳回联动关闭", en: "Rejection Cascade & Closing"}
description:
  zh: >
      驳回发生后的联动关闭：会签节点中任一人驳回即节点立即驳回、其余未处理任务自动关闭（REQ-FLOW-015）；任一协同部门驳回则单据回到发起人，其余协同任务自动关闭（REQ-FLOW-016）；节点与实例的其余任务、其余节点实例按状态机联动取消或关闭，防止挂单。
      
  en: >
      Cascade closing after a rejection: on a countersign node any single rejection rejects the node immediately and auto-closes its remaining tasks; a rejection from any collaborating department returns the document to the initiator and closes the other collaboration tasks. Remaining tasks and node instances follow the state machine so no document is left hanging.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.368Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-015`（§6.6 异常路径）
