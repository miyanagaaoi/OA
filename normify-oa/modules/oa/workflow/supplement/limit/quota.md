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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.786Z"
fingerprint: 4421092e639ef98e9cd4aac53b5975054268b604256d140e3544a8a3c2ba5551
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
