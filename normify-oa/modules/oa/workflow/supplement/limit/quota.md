---
uid: 586879bf
id: oa.workflow.supplement.limit.quota
parent: oa.workflow.supplement.limit
state: planned
name: {zh: "补件次数控制", en: "Supplement Quota"}
description:
  zh: >
      补件次数上限控制：同一节点最多请求 1 次（节点实例 supplement_requested 标记），全单累计最多 3 次（实例 supplement_count，与 flow_supplement 的轮次唯一键一致）；达上限后发起补件动作不再出现，审批人只能在通过、驳回、终止中选择。
      
  en: >
      Supplement caps: a node may request a supplement only once (node instance flag supplement_requested) and a document accumulates at most three (instance supplement_count, consistent with the unique key on instance and round). Once the cap is reached the supplement action disappears and the approver may only pass, reject or terminate.
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.827Z"
fingerprint: 6be246c6834b9b56d4a42d5b955150bb6a06a766a90c031fd7f3af6b4c917a6d
source:
  - path: "doc/prd-0.1.md"
    line: 333
    end_line: 333
  - path: "doc/prd-0.1.md"
    line: 554
    end_line: 554
  - path: "doc/data-model.md"
    line: 401
    end_line: 401
  - path: "doc/data-model.md"
    line: 436
    end_line: 436
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/instances/{instance_id}/supplement-quota/check"
    description:
      zh: >
          校验同节点 ≤1 次、全单 ≤3 次。
          
      en: >
          Check the once-per-node and three-per-document caps.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow/instances/{instance_id}/supplement-quota"
    description:
      zh: >
          读取已用补件次数与是否仍可请求。
          
      en: >
          Read used supplement rounds and whether another request is allowed.
          
---
