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
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.333Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
  - path: "doc/data-model.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-023`（§6.3.1 集团层流转机制）
- `doc/data-model.md` → `CREATE TABLE flow_supplement`（§5. 流程运行时）
