---
uid: 42fcca30
id: oa.workflow.supplement.pending
parent: oa.workflow.supplement
name: {zh: "待补件子状态", en: "Pending-Supplement Sub-status"}
description:
  zh: >
      实例主状态仍为「审批中」，子状态置 pending_supplement；当前节点实例置 waiting_supplement，该节点任务状态为 supplement_requested（不可再审批）；补件期间单据对其他角色（其他部门、抄送人）保持只读可见但不可审批，避免待补件单据被别人审掉。
      
  en: >
      The instance keeps its approving main status but takes the pending_supplement sub-status; the current node instance becomes waiting_supplement and that node's tasks become supplement_requested, i.e. no longer approvable. While waiting, the document stays read-only visible to other roles (other departments, CC recipients) but nobody may approve it.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.166Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/instances/{instance_id}/sub-status/supplement"
    description:
      zh: >
          置/清「待补件」子状态。
          
      en: >
          Set or clear the pending-supplement sub-status.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow/instances/{instance_id}/sub-status"
    description:
      zh: >
          读取实例子状态与当前节点状态。
          
      en: >
          Read the instance sub-status and current node status.
          
  - protocol: http
    method: POST
    path: "/api/v1/flow/instances/{instance_id}/approval-lock"
    description:
      zh: >
          待补件期间锁定审批入口（只读可见、不可审批）。
          
      en: >
          Lock approval during pending supplement (read-only).
          
deps:
  - kind: call
    to: oa.workflow.runtime
    from_api: "POST /api/v1/flow/instances/{instance_id}/sub-status/supplement"
    label: {zh: "子状态与节点联动", en: "Apply sub-status cascade"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-023`（§6.3.1 集团层流转机制）
- `doc/data-model.md` → `CREATE TABLE flow_supplement`（§5. 流程运行时）
