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
      
revision: 44fc7aba1c7e884ffa3553faf31bfce974b388a9
updated_at: "2026-10-03T04:12:20.811Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
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
