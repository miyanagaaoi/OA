---
uid: 522af6c1
id: oa.workflow.runtime.counters
parent: oa.workflow.runtime
state: planned
name: {zh: "计数与闸门", en: "Counters & Gates"}
description:
  zh: >
      三个运行计数的原子维护与上限闸门：routing_count（流转+回退累计 ≤5）、supplement_count（补件 ≤3）、returned_count（同一节点被回退 ≤2）；达到上限即拒绝操作并提示改用「驳回」或「终止」，超限动作必须写审计（REQ-FLOW-021/022/023/024、AC-23）。
      
  en: >
      Atomic maintenance and gates for the three runtime counters: routing_count (routing+return, cap 5), supplement_count (cap 3) and returned_count (cap 2 per node); once a limit is reached the operation is refused with a hint to use reject or terminate and the refusal is written to the audit log (REQ-FLOW-021/022/023/024, AC-23).
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.324Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/flow-instances/{instance_id}/counters"
    description:
      zh: >
          读取三个运行计数与其上限。
          
      en: >
          Reads routing_count, supplement_count and returned_count with their caps.
          
  - protocol: http
    method: POST
    path: "/api/v1/flow-instances/{instance_id}/counters/increment"
    description:
      zh: >
          按动作原子自增对应计数。
          
      en: >
          Atomically increments the counters matching the action.
          
  - protocol: http
    method: POST
    path: "/api/v1/flow-instances/{instance_id}/counters/check"
    description:
      zh: >
          校验上限（5/3/2），超限拒绝并提示改用驳回或终止。
          
      en: >
          Checks the caps (5/3/2) and refuses with a hint to use reject or terminate.
          
  - protocol: kafka
    path: "oa.workflow.instance.counter-limit-reached"
    description:
      zh: >
          计数上限拒绝操作时发布的事件。
          
      en: >
          Event emitted when a counter cap refuses an operation.
          
deps:
  - kind: reference
    to: oa.workflow.routing
    label: {zh: "与流转链对齐", en: "Align with routing chain"}
  - kind: reference
    to: oa.workflow.supplement
    label: {zh: "与补件轮次对齐", en: "Align with supplement rounds"}
  - kind: dataflow
    to: oa.audit.oplog
    from_api: "POST /api/v1/flow-instances/{instance_id}/counters/check"
    label: {zh: "超限拒绝写日志", en: "Refusals to audit log"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-024`（§6.4 流程引擎核心能力）
- `doc/data-model.md` → `CREATE TABLE flow_routing`（§5. 流程运行时）
