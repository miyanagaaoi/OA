---
uid: 11a75654
id: oa.workflow.task.decision.mode
parent: oa.workflow.task.decision
state: planned
name: {zh: "或签/会签/依次审批", en: "Any-sign / Countersign / Sequential"}
description:
  zh: >
      三种决议模式在同一节点上的判定：或签任一人同意即节点通过并自动关闭其余任务；会签按同意人数累计判定，任一人驳回即节点驳回；依次审批按配置顺序串行派发，全部通过才通过。节点的决议模式在发起时冻结到节点实例，运行期不可更改。
      
  en: >
      How the three modes decide a node: any-sign passes on the first approval and closes the rest; countersign accumulates approvals and rejects the node on any rejection; sequential dispatches in configured order and requires all to approve. The mode is frozen into the node instance at submission time.
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.790Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 220
    end_line: 224
  - path: "doc/prd-0.1.md"
    line: 350
    end_line: 350
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/node-instances/{node_instance_id}/decisions"
    description:
      zh: >
          提交本节点决议并按决议模式判定节点结果。
          
      en: >
          Submit a decision and evaluate the node result by decision mode.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow/node-instances/{node_instance_id}/tally"
    description:
      zh: >
          读取节点同意/驳回/待处理票数与判定进度。
          
      en: >
          Read the node tally: agreed, rejected, pending and progress.
          
  - protocol: kafka
    path: "oa.workflow.node.decided"
    description:
      zh: >
          节点决议完成事件（供实例推进与通知）。
          
      en: >
          Event emitted when a node decision completes.
          
deps:
  - kind: call
    to: oa.workflow.task.record
    from_api: "POST /api/v1/flow/node-instances/{node_instance_id}/decisions"
    to_api: "mysql:flow_task"
    label: {zh: "写回任务决议与意见", en: "Persist decision and opinion"}
  - kind: call
    to: oa.workflow.runtime
    from_api: "POST /api/v1/flow/node-instances/{node_instance_id}/decisions"
    label: {zh: "节点通过后推进实例", en: "Advance instance to next node"}
---
