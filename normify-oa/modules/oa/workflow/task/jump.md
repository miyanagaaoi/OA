---
uid: 2461265c
id: oa.workflow.task.jump
parent: oa.workflow.task
name: {zh: "自由跳转", en: "Free Jump"}
description:
  zh: >
      仅有明确授权的节点可跳转（默认关闭，需管理员在流程模板中逐节点开启）；跳转必须填写原因，并记入审计日志与审批轨迹；未授权节点调用一律拒绝。跳转不改变已完成的审批事实。
      
  en: >
      Only explicitly authorized nodes may jump (off by default, enabled per node in the template); a reason is mandatory and every jump is written to the audit log and approval trail. Unauthorized calls are rejected and completed approvals are never altered.
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.426Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/instances/{instance_id}/jump"
    description:
      zh: >
          跳转到指定节点（须授权 + 原因必填）。
          
      en: >
          Jump to a target node with authorization and a mandatory reason.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow/instances/{instance_id}/jump-targets"
    description:
      zh: >
          当前操作人可跳转的目标节点清单（按模板授权）。
          
      en: >
          Jump targets authorized for the current actor.
          
deps:
  - kind: call
    to: oa.workflow.definition
    from_api: "GET /api/v1/flow/instances/{instance_id}/jump-targets"
    label: {zh: "读取模板跳转授权", en: "Read jump authorization"}
  - kind: call
    to: oa.audit.trace
    from_api: "POST /api/v1/flow/instances/{instance_id}/jump"
    label: {zh: "跳转记入审批轨迹", en: "Record jump in trail"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-004`（§6.4 流程引擎核心能力）
