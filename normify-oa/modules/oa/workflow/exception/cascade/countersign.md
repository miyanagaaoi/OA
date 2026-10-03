---
uid: 67e35a89
id: oa.workflow.exception.cascade.countersign
parent: oa.workflow.exception.cascade
state: planned
name: {zh: "会签驳回联动", en: "Countersign Rejection Cascade"}
description:
  zh: >
      会签节点中任一人驳回 → 该节点立即驳回 → 单据回到发起人；同节点其余未处理任务全部置为「已自动关闭」，不再等待剩余会签人；已达阈值的历史同意记录保留在轨迹中，但节点结论为驳回。
      
  en: >
      Any single rejection on a countersign node rejects the node immediately and returns the document to the initiator; every other pending task of that node becomes auto-closed instead of waiting for the remaining signers. Approvals already recorded stay in the trail while the node verdict is rejection.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.600Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/node-instances/{node_instance_id}/reject-cascade"
    description:
      zh: >
          会签节点驳回联动（节点驳回 + 其余任务自动关闭）。
          
      en: >
          Countersign rejection cascade: reject node and close remaining tasks.
          
  - protocol: kafka
    path: "oa.workflow.node.rejected"
    description:
      zh: >
          节点驳回事件（驱动实例回发起人）。
          
      en: >
          Event emitted when a node is rejected.
          
deps:
  - kind: call
    to: oa.workflow.runtime
    from_api: "kafka:oa.workflow.node.rejected"
    label: {zh: "节点与实例状态联动", en: "Cascade node state"}
  - kind: call
    to: oa.workflow.task.record
    from_api: "POST /api/v1/flow/node-instances/{node_instance_id}/reject-cascade"
    to_api: "POST /api/v1/flow/tasks/{task_id}/close"
    label: {zh: "其余任务自动关闭", en: "Auto-close remaining tasks"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-015`（§6.6 异常路径）
