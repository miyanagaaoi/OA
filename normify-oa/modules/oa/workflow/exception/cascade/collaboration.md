---
uid: 683a778e
id: oa.workflow.exception.cascade.collaboration
parent: oa.workflow.exception.cascade
name: {zh: "协同部门驳回联动", en: "Collaboration Rejection Cascade"}
description:
  zh: >
      任一协同部门驳回 → 单据回到发起人，其余协同任务（其他协同部门的任务组）自动关闭；协同任务的驳回视同该并行子任务的驳回，不等待其余协同部门完成。
      
  en: >
      A rejection from any collaborating department returns the document to the initiator and automatically closes the remaining collaboration task groups; the rejection of a collaboration task counts as the rejection of that parallel sub-task and does not wait for the other departments to finish.
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.407Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/instances/{instance_id}/collaboration-reject-cascade"
    description:
      zh: >
          协同部门驳回联动（回发起人 + 其余协同任务关闭）。
          
      en: >
          Collaboration rejection cascade: return to initiator and close other tasks.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow/instances/{instance_id}/collaboration-open-tasks"
    description:
      zh: >
          待关闭的协同任务清单。
          
      en: >
          Collaboration tasks still open and about to be closed.
          
deps:
  - kind: call
    to: oa.workflow.task
    from_api: "POST /api/v1/flow/instances/{instance_id}/collaboration-reject-cascade"
    label: {zh: "关闭其余协同任务组", en: "Close other collab groups"}
  - kind: call
    to: oa.workflow.runtime
    label: {zh: "实例回发起人", en: "Return instance to initiator"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-016`（§6.6 异常路径）
