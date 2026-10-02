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
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.819Z"
fingerprint: 6be246c6834b9b56d4a42d5b955150bb6a06a766a90c031fd7f3af6b4c917a6d
source:
  - path: "doc/prd-0.1.md"
    line: 337
    end_line: 337
  - path: "doc/prd-0.1.md"
    line: 551
    end_line: 551
  - path: "doc/data-model.md"
    line: 400
    end_line: 400
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
