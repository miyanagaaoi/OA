---
uid: 35c8e98f
id: oa.workflow.routing.gate.quota
parent: oa.workflow.routing.gate
state: planned
name: {zh: "总次数闸门（≤5）", en: "Total Count Gate (max 5)"}
description:
  zh: >
      流转 + 回退合计次数上限默认 5（可配置）：每次流转或回退累加 routing_count，达到上限后系统拒绝继续流转/回退且不产生任何状态变更，并返回具体原因、提示改用「驳回」或「终止」。
      
  en: >
      Routing plus rollback share a default cap of five hops (configurable): each route or rollback increments routing_count; once the cap is reached the system refuses further routing or rollback, changes no state and returns a concrete reason suggesting rejection or termination instead.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.431Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/instances/{instance_id}/routing-quota/check"
    description:
      zh: >
          校验流转+回退总次数是否已达上限（达上限拒绝）。
          
      en: >
          Check whether routing plus rollback has reached the cap and refuse if so.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow/instances/{instance_id}/routing-quota"
    description:
      zh: >
          读取已用与剩余流转/回退次数。
          
      en: >
          Read used and remaining routing and rollback counts.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-024`（§6.4 流程引擎核心能力）
- `doc/data-model.md` → `CREATE TABLE flow_routing`（§5. 流程运行时）
